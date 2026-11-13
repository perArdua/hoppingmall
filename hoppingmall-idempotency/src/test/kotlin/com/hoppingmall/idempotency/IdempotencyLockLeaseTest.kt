package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import redis.embedded.RedisServer
import java.net.ServerSocket
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("멱등성 락의 처리 시간 동안 유지")
@DisplayNameGeneration(ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotencyLockLeaseTest {

    private var redisServer: RedisServer? = null
    private var up = false
    private lateinit var redisson: RedissonClient

    @BeforeAll
    fun start() {
        try {
            val port = ServerSocket(0).use { it.localPort }
            redisServer = RedisServer(port).also { it.start() }
            redisson = Redisson.create(Config().apply {
                useSingleServer().address = "redis://127.0.0.1:$port"
                lockWatchdogTimeout = WATCHDOG_TIMEOUT_MS
            })
            up = true
        } catch (e: Exception) {
            up = false
        }
    }

    @AfterAll
    fun stop() {
        if (::redisson.isInitialized) runCatching { redisson.shutdown() }
        runCatching { redisServer?.stop() }
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Idempotent(ttlHours = 24)
    fun 주문_생성() = Unit

    @Test
    fun 처리가_고정_lease보다_길어져도_같은_키의_두_번째_요청은_동시에_처리되지_않는다() {
        assumeTrue(up)
        val aspect = IdempotencyAspect(mock<IdempotencyService>(), redisson, ObjectMapper())
        val idempotent = javaClass.getDeclaredMethod("주문_생성").getAnnotation(Idempotent::class.java)
        val inProgress = AtomicInteger()
        val maxInProgress = AtomicInteger()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = mock<ProceedingJoinPoint>()
        whenever(first.proceed()).thenAnswer {
            maxInProgress.accumulateAndGet(inProgress.incrementAndGet(), ::maxOf)
            firstStarted.countDown()
            releaseFirst.await(60, TimeUnit.SECONDS)
            inProgress.decrementAndGet()
            ResponseEntity.ok(mapOf("orderId" to 1))
        }
        val second = mock<ProceedingJoinPoint>()
        whenever(second.proceed()).thenAnswer {
            maxInProgress.accumulateAndGet(inProgress.incrementAndGet(), ::maxOf)
            inProgress.decrementAndGet()
            ResponseEntity.ok(mapOf("orderId" to 2))
        }

        val executor = Executors.newSingleThreadExecutor()
        try {
            val firstResult = executor.submit(Callable { send(aspect, first, idempotent) })
            assertThat(firstStarted.await(10, TimeUnit.SECONDS)).isTrue()

            waitUntilOldFixedLeasePassedOrLockReleased()

            val secondOutcome = runCatching { send(aspect, second, idempotent) }
            releaseFirst.countDown()
            val firstResponse = firstResult.get(30, TimeUnit.SECONDS)

            assertThat(secondOutcome.exceptionOrNull()).isInstanceOf(IdempotencyConflictException::class.java)
            assertThat(maxInProgress.get()).isEqualTo(1)
            verify(second, never()).proceed()
            assertThat(firstResponse.statusCode.is2xxSuccessful).isTrue()
            assertThat(redisson.keys.getKeysByPattern("idempotency:*").toList()).isEmpty()
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private fun send(aspect: IdempotencyAspect, joinPoint: ProceedingJoinPoint, idempotent: Idempotent): ResponseEntity<*> {
        val request = MockHttpServletRequest("POST", "/api/v1/orders")
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, "order-key")
        request.addHeader("X-User-Id", "1")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            return aspect.handleIdempotency(joinPoint, idempotent) as ResponseEntity<*>
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    private fun waitUntilOldFixedLeasePassedOrLockReleased() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(OLD_FIXED_LEASE_SECONDS + 2)
        while (System.nanoTime() < deadline && redisson.keys.getKeysByPattern("idempotency:*").any()) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val WATCHDOG_TIMEOUT_MS = 3_000L
        private const val OLD_FIXED_LEASE_SECONDS = 10L
        private const val POLL_INTERVAL_MS = 200L
    }
}
