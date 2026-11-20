package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.concurrent.TimeUnit

@DisplayName("IdempotencyAspect 트랜잭션 안 기록 경로")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class IdempotencyAspectCommitPathTest {

    @Mock
    private lateinit var idempotencyService: IdempotencyService

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var lock: RLock

    @Mock
    private lateinit var joinPoint: ProceedingJoinPoint

    @Mock
    private lateinit var idempotent: Idempotent

    private lateinit var aspect: IdempotencyAspect

    @BeforeEach
    fun setUp() {
        aspect = IdempotencyAspect(idempotencyService, redissonClient, ObjectMapper())
        val request = MockHttpServletRequest("POST", "/api/v1/orders")
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, "commit-key")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
        IdempotencyRequestContext.clear()
    }

    private fun stubLock() {
        whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
    }

    @Nested
    @DisplayName("플래그가 모두 꺼짐")
    inner class FlagsOff {

        @Test
        fun 표시된_엔드포인트도_보관함을_열지_않고_락을_잡은_뒤_별도_트랜잭션으로_저장한다() {
            stubLock()
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            var opened: PendingIdempotency? = null
            whenever(joinPoint.proceed()).thenAnswer {
                opened = IdempotencyRequestContext.current()
                ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to 1))
            }

            aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(opened).isNull()
            verify(idempotencyService).save(any(), eq("POST"), eq("/api/v1/orders"), eq(201), any(), any(), eq(24L))
            verify(lock).unlock()
        }
    }

    @Nested
    @DisplayName("save-in-tx 켬")
    inner class SaveInTx {

        @BeforeEach
        fun enable() {
            ReflectionTestUtils.setField(aspect, "saveInTx", true)
        }

        @Test
        fun 진행_중_트랜잭션_안에서_저장했으면_다시_저장하지_않고_보관함을_닫는다() {
            stubLock()
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            val response = ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to 1))
            var opened: PendingIdempotency? = null
            whenever(joinPoint.proceed()).thenAnswer {
                opened = IdempotencyRequestContext.current()
                opened!!.savedInTransaction = true
                response
            }

            val result = aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(result).isEqualTo(response)
            assertThat(opened!!.ttlHours).isEqualTo(24L)
            assertThat(IdempotencyRequestContext.current()).isNull()
            verify(idempotencyService, never()).save(any(), any(), any(), any(), any(), any(), any())
            verify(lock).unlock()
        }

        @Test
        fun 진행_중_저장하지_않았으면_예전처럼_별도_트랜잭션으로_저장한다() {
            stubLock()
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            whenever(joinPoint.proceed()).thenReturn(ResponseEntity.ok(mapOf("id" to 2)))

            aspect.handleIdempotency(joinPoint, idempotent)

            verify(idempotencyService).save(any(), eq("POST"), eq("/api/v1/orders"), eq(200), any(), any(), eq(24L))
            assertThat(IdempotencyRequestContext.current()).isNull()
        }

        @Test
        fun 진행_중_예외가_나도_보관함을_닫고_락을_푼다() {
            stubLock()
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            whenever(joinPoint.proceed()).thenThrow(IllegalStateException("boom"))

            assertThatThrownBy { aspect.handleIdempotency(joinPoint, idempotent) }.hasMessage("boom")

            assertThat(IdempotencyRequestContext.current()).isNull()
            verify(lock).unlock()
        }
    }
}
