package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@DisplayName("멱등성 키의 요청자·메서드·경로 묶기")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class IdempotencyRequesterScopeTest {

    @Mock
    private lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var lock: RLock

    @Mock
    private lateinit var joinPoint: ProceedingJoinPoint

    @Mock
    private lateinit var idempotent: Idempotent

    private val objectMapper = ObjectMapper()

    private val store = mutableMapOf<String, IdempotencyRecord>()

    private lateinit var aspect: IdempotencyAspect

    @BeforeEach
    fun setUp() {
        aspect = IdempotencyAspect(IdempotencyService(idempotencyRecordRepository), redissonClient, objectMapper)
        whenever(idempotencyRecordRepository.findByIdempotencyKey(any())).thenAnswer { store[it.getArgument(0)] }
        whenever(idempotencyRecordRepository.save(any<IdempotencyRecord>())).thenAnswer {
            val record = it.getArgument<IdempotencyRecord>(0)
            store[record.idempotencyKey] = record
            record
        }
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
        whenever(idempotent.ttlHours).thenReturn(24L)
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    private fun send(userId: String?, method: String, path: String, key: String): ResponseEntity<*> {
        val request = MockHttpServletRequest(method, path)
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, key)
        userId?.let { request.addHeader("X-User-Id", it) }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return aspect.handleIdempotency(joinPoint, idempotent) as ResponseEntity<*>
    }

    @Test
    fun 다른_사용자가_같은_키를_보내면_남의_응답을_받지_않고_자기_요청이_처리된다() {
        whenever(joinPoint.proceed())
            .thenReturn(ResponseEntity.status(201).body(mapOf("orderId" to 1)))
            .thenReturn(ResponseEntity.status(201).body(mapOf("orderId" to 2)))

        send("1", "POST", "/api/v1/orders", "shared-key")
        val second = send("2", "POST", "/api/v1/orders", "shared-key")

        assertThat(second.body).isEqualTo(mapOf("orderId" to 2))
        verify(joinPoint, times(2)).proceed()
    }

    @Test
    fun 같은_사용자가_같은_키를_다른_경로에_보내면_섞이지_않는다() {
        whenever(joinPoint.proceed())
            .thenReturn(ResponseEntity.status(201).body(mapOf("orderId" to 1)))
            .thenReturn(ResponseEntity.status(201).body(mapOf("refundId" to 7)))

        send("1", "POST", "/api/v1/orders", "same-key")
        val second = send("1", "POST", "/api/v1/refunds", "same-key")

        assertThat(second.body).isEqualTo(mapOf("refundId" to 7))
        verify(joinPoint, times(2)).proceed()
    }

    @Test
    fun 같은_사용자가_같은_키를_다른_메서드로_보내면_섞이지_않는다() {
        whenever(joinPoint.proceed())
            .thenReturn(ResponseEntity.status(201).body(mapOf("reviewId" to 3)))
            .thenReturn(ResponseEntity.ok(mapOf("updated" to true)))

        send("1", "POST", "/api/v1/reviews/3", "same-key")
        val second = send("1", "PUT", "/api/v1/reviews/3", "same-key")

        assertThat(second.body).isEqualTo(mapOf("updated" to true))
        verify(joinPoint, times(2)).proceed()
    }

    @Test
    fun 같은_사용자가_같은_경로에_같은_키를_다시_보내면_저장된_응답을_재생한다() {
        whenever(joinPoint.proceed())
            .thenReturn(ResponseEntity.status(201).body(mapOf("orderId" to 1)))

        send("1", "POST", "/api/v1/orders", "retry-key")
        val second = send("1", "POST", "/api/v1/orders", "retry-key")

        assertThat(second.statusCode.value()).isEqualTo(201)
        assertThat(second.body).isEqualTo(mapOf("orderId" to 1))
        verify(joinPoint, times(1)).proceed()
    }

    @Test
    fun 다른_사용자의_같은_키는_서로_다른_락을_잡는다() {
        whenever(joinPoint.proceed())
            .thenReturn(ResponseEntity.status(201).body(mapOf("orderId" to 1)))
            .thenReturn(ResponseEntity.status(201).body(mapOf("orderId" to 2)))

        send("1", "POST", "/api/v1/orders", "shared-key")
        send("2", "POST", "/api/v1/orders", "shared-key")

        val lockNames = argumentCaptor<String>()
        verify(redissonClient, times(2)).getLock(lockNames.capture())
        assertThat(lockNames.firstValue).isNotEqualTo(lockNames.secondValue)
    }

    @Test
    fun 요청자에_묶이지_않은_옛_형식의_키는_찾지_못하고_요청을_처리한다() {
        store["legacy-key"] = IdempotencyRecord(
            idempotencyKey = "legacy-key",
            httpMethod = "POST",
            endpoint = "/api/v1/orders",
            responseStatus = 201,
            responseBody = objectMapper.writeValueAsString(mapOf("orderId" to 99)),
            expiresAt = LocalDateTime.now().plusHours(1)
        )
        whenever(joinPoint.proceed())
            .thenReturn(ResponseEntity.status(201).body(mapOf("orderId" to 5)))

        val result = send("1", "POST", "/api/v1/orders", "legacy-key")

        assertThat(result.body).isEqualTo(mapOf("orderId" to 5))
        verify(joinPoint, times(1)).proceed()
    }
}
