package com.hoppingmall.mall.global.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@DisplayName("IdempotencyAspect")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IdempotencyAspectTest {

    private val idempotencyService: IdempotencyService = mock()
    private val redissonClient: RedissonClient = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val aspect = IdempotencyAspect(idempotencyService, redissonClient, objectMapper)

    private val joinPoint: ProceedingJoinPoint = mock()
    private val idempotent = mock<Idempotent>()
    private val lock: RLock = mock()

    @BeforeEach
    fun setUp() {
        whenever(idempotent.ttlHours).thenReturn(24)
        whenever(idempotent.lockTimeoutSeconds).thenReturn(10)
    }

    private fun setUpRequest(idempotencyKey: String? = null): MockHttpServletRequest {
        val request = MockHttpServletRequest("POST", "/api/v1/payments")
        idempotencyKey?.let { request.addHeader("Idempotency-Key", it) }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return request
    }

    @Nested
    @DisplayName("handleIdempotency")
    inner class HandleIdempotency {

        @Test
        fun 헤더가_없으면_예외가_발생한다() {
            // given
            setUpRequest(idempotencyKey = null)

            // when & then
            assertThrows<IdempotencyKeyMissingException> {
                aspect.handleIdempotency(joinPoint, idempotent)
            }
        }

        @Test
        fun 캐시_히트_시_저장된_응답을_반환한다() {
            // given
            val key = "cached-key"
            setUpRequest(idempotencyKey = key)

            val cachedRecord = IdempotencyRecord(
                id = 1L,
                idempotencyKey = key,
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 200,
                responseBody = """{"id":1,"amount":50000}""",
                expiresAt = LocalDateTime.now().plusHours(1)
            )

            whenever(idempotencyService.findByKey(key)).thenReturn(cachedRecord)

            // when
            val result = aspect.handleIdempotency(joinPoint, idempotent)

            // then
            assertTrue(result is ResponseEntity<*>)
            val response = result as ResponseEntity<*>
            assertEquals(200, response.statusCode.value())
            verify(joinPoint, never()).proceed()
        }

        @Test
        fun 새_요청은_실행_후_결과를_저장한다() {
            // given
            val key = "new-key"
            setUpRequest(idempotencyKey = key)

            whenever(idempotencyService.findByKey(key)).thenReturn(null)
            whenever(redissonClient.getLock("idempotency:$key")).thenReturn(lock)
            whenever(lock.tryLock(0, 10, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)

            val responseBody = mapOf("id" to 1, "amount" to 50000)
            whenever(joinPoint.proceed()).thenReturn(ResponseEntity.ok(responseBody))

            val savedRecord = IdempotencyRecord(
                id = 1L,
                idempotencyKey = key,
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 200,
                responseBody = objectMapper.writeValueAsString(responseBody),
                expiresAt = LocalDateTime.now().plusHours(24)
            )
            whenever(idempotencyService.save(eq(key), any(), any(), any(), any(), any()))
                .thenReturn(savedRecord)

            // when
            val result = aspect.handleIdempotency(joinPoint, idempotent)

            // then
            assertTrue(result is ResponseEntity<*>)
            val response = result as ResponseEntity<*>
            assertEquals(200, response.statusCode.value())
            verify(joinPoint).proceed()
            verify(idempotencyService).save(eq(key), eq("POST"), eq("/api/v1/payments"), eq(200), any(), eq(24))
            verify(lock).unlock()
        }

        @Test
        fun 락_획득_실패_시_예외가_발생한다() {
            // given
            val key = "conflict-key"
            setUpRequest(idempotencyKey = key)

            whenever(idempotencyService.findByKey(key)).thenReturn(null)
            whenever(redissonClient.getLock("idempotency:$key")).thenReturn(lock)
            whenever(lock.tryLock(0, 10, TimeUnit.SECONDS)).thenReturn(false)

            // when & then
            assertThrows<IdempotencyConflictException> {
                aspect.handleIdempotency(joinPoint, idempotent)
            }
            verify(joinPoint, never()).proceed()
        }

        @Test
        fun 락_후_이중_체크에서_캐시_히트_시_실행하지_않는다() {
            // given
            val key = "double-check-key"
            setUpRequest(idempotencyKey = key)

            val cachedRecord = IdempotencyRecord(
                id = 1L,
                idempotencyKey = key,
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 200,
                responseBody = """{"id":1}""",
                expiresAt = LocalDateTime.now().plusHours(1)
            )

            whenever(idempotencyService.findByKey(key))
                .thenReturn(null)
                .thenReturn(cachedRecord)
            whenever(redissonClient.getLock("idempotency:$key")).thenReturn(lock)
            whenever(lock.tryLock(0, 10, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)

            // when
            val result = aspect.handleIdempotency(joinPoint, idempotent)

            // then
            assertTrue(result is ResponseEntity<*>)
            verify(joinPoint, never()).proceed()
            verify(lock).unlock()
        }

        @Test
        fun 실행_중_예외_발생_시_레코드를_저장하지_않는다() {
            // given
            val key = "error-key"
            setUpRequest(idempotencyKey = key)

            whenever(idempotencyService.findByKey(key)).thenReturn(null)
            whenever(redissonClient.getLock("idempotency:$key")).thenReturn(lock)
            whenever(lock.tryLock(0, 10, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)
            whenever(joinPoint.proceed()).thenThrow(RuntimeException("결제 실패"))

            // when & then
            assertThrows<RuntimeException> {
                aspect.handleIdempotency(joinPoint, idempotent)
            }
            verify(idempotencyService, never()).save(any(), any(), any(), any(), any(), any())
            verify(lock).unlock()
        }
    }
}
