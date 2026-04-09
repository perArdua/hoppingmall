package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.aspectj.lang.ProceedingJoinPoint
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@DisplayName("IdempotencyAspect")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class IdempotencyAspectTest {

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

    private val objectMapper = ObjectMapper()

    private lateinit var aspect: IdempotencyAspect

    @BeforeEach
    fun setUp() {
        aspect = IdempotencyAspect(idempotencyService, redissonClient, objectMapper)
    }

    private fun setUpRequest(path: String = "/api/v1/orders", method: String = "POST", idempotencyKey: String? = "test-key"): MockHttpServletRequest {
        val request = MockHttpServletRequest(method, path)
        if (idempotencyKey != null) {
            request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return request
    }

    @BeforeEach
    fun clearRequestContext() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Nested
    @DisplayName("요청 컨텍스트 없음")
    inner class NoRequestContext {

        @Test
        fun 요청_컨텍스트가_없으면_그대로_진행한다() {
            RequestContextHolder.resetRequestAttributes()
            val expected = ResponseEntity.ok("result")
            whenever(joinPoint.proceed()).thenReturn(expected)

            val result = aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(result).isEqualTo(expected)
            verify(idempotencyService, never()).findByKey(any())
        }
    }

    @Nested
    @DisplayName("Idempotency-Key 헤더 누락")
    inner class MissingHeader {

        @Test
        fun 헤더가_없으면_예외를_던진다() {
            setUpRequest(idempotencyKey = null)

            assertThatThrownBy {
                aspect.handleIdempotency(joinPoint, idempotent)
            }.isInstanceOf(IdempotencyKeyMissingException::class.java)

            verify(idempotencyService, never()).findByKey(any())
        }
    }

    @Nested
    @DisplayName("캐시 히트")
    inner class CacheHit {

        @Test
        fun 기존_레코드가_있으면_캐시된_응답을_반환한다() {
            setUpRequest()
            val body = mapOf("id" to 1)
            val record = IdempotencyRecord(
                id = 1L,
                idempotencyKey = "test-key",
                httpMethod = "POST",
                endpoint = "/api/v1/orders",
                responseStatus = 200,
                responseBody = objectMapper.writeValueAsString(body),
                expiresAt = LocalDateTime.now().plusHours(1)
            )
            whenever(idempotencyService.findByKey("test-key")).thenReturn(record)

            @Suppress("UNCHECKED_CAST")
            val result = aspect.handleIdempotency(joinPoint, idempotent) as ResponseEntity<Any>

            assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
            verify(joinPoint, never()).proceed()
            verify(redissonClient, never()).getLock(any<String>())
        }

        @Test
        fun 락_획득_후_이중_체크에서_히트하면_캐시된_응답을_반환한다() {
            setUpRequest()
            whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
            whenever(redissonClient.getLock(eq("idempotency:test-key"))).thenReturn(lock)
            whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)

            val body = mapOf("status" to "ok")
            val record = IdempotencyRecord(
                id = 2L,
                idempotencyKey = "test-key",
                httpMethod = "POST",
                endpoint = "/api/v1/orders",
                responseStatus = 201,
                responseBody = objectMapper.writeValueAsString(body),
                expiresAt = LocalDateTime.now().plusHours(1)
            )
            whenever(idempotencyService.findByKey("test-key"))
                .thenReturn(null)
                .thenReturn(record)

            @Suppress("UNCHECKED_CAST")
            val result = aspect.handleIdempotency(joinPoint, idempotent) as ResponseEntity<Any>

            assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
            verify(joinPoint, never()).proceed()
            verify(lock).unlock()
        }
    }

    @Nested
    @DisplayName("락 획득 실패")
    inner class LockAcquisitionFailure {

        @Test
        fun 락_획득에_실패하면_충돌_예외를_던진다() {
            setUpRequest()
            whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
            whenever(idempotencyService.findByKey("test-key")).thenReturn(null)
            whenever(redissonClient.getLock(eq("idempotency:test-key"))).thenReturn(lock)
            whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(false)

            assertThatThrownBy {
                aspect.handleIdempotency(joinPoint, idempotent)
            }.isInstanceOf(IdempotencyConflictException::class.java)

            verify(joinPoint, never()).proceed()
        }
    }

    @Nested
    @DisplayName("정상 처리 및 저장")
    inner class SuccessfulProcessingAndSave {

        @Test
        fun 성공_응답이면_레코드를_저장하고_결과를_반환한다() {
            setUpRequest(path = "/api/v1/orders", method = "POST")
            whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey("test-key")).thenReturn(null)
            whenever(redissonClient.getLock(eq("idempotency:test-key"))).thenReturn(lock)
            whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)

            val responseBody = mapOf("orderId" to 42)
            val response = ResponseEntity.status(201).body(responseBody)
            whenever(joinPoint.proceed()).thenReturn(response)

            val savedRecord = IdempotencyRecord(
                id = 3L,
                idempotencyKey = "test-key",
                httpMethod = "POST",
                endpoint = "/api/v1/orders",
                responseStatus = 201,
                responseBody = objectMapper.writeValueAsString(responseBody),
                expiresAt = LocalDateTime.now().plusHours(24)
            )
            whenever(idempotencyService.save(any(), any(), any(), any(), any(), any()))
                .thenReturn(savedRecord)

            val result = aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(result).isEqualTo(response)
            verify(idempotencyService).save(
                key = eq("test-key"),
                httpMethod = eq("POST"),
                endpoint = eq("/api/v1/orders"),
                responseStatus = eq(201),
                responseBody = any(),
                ttlHours = eq(24L)
            )
            verify(lock).unlock()
        }

        @Test
        fun 비2xx_응답이면_레코드를_저장하지_않는다() {
            setUpRequest()
            whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
            whenever(idempotencyService.findByKey("test-key")).thenReturn(null)
            whenever(redissonClient.getLock(eq("idempotency:test-key"))).thenReturn(lock)
            whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)

            val response = ResponseEntity.status(400).body("bad request")
            whenever(joinPoint.proceed()).thenReturn(response)

            val result = aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(result).isEqualTo(response)
            verify(idempotencyService, never()).save(any(), any(), any(), any(), any(), any())
            verify(lock).unlock()
        }

        @Test
        fun ResponseEntity가_아닌_반환값이면_저장하지_않는다() {
            setUpRequest()
            whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
            whenever(idempotencyService.findByKey("test-key")).thenReturn(null)
            whenever(redissonClient.getLock(eq("idempotency:test-key"))).thenReturn(lock)
            whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)

            whenever(joinPoint.proceed()).thenReturn("plain string")

            val result = aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(result).isEqualTo("plain string")
            verify(idempotencyService, never()).save(any(), any(), any(), any(), any(), any())
            verify(lock).unlock()
        }

        @Test
        fun 처리_중_예외가_발생해도_락을_해제한다() {
            setUpRequest()
            whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
            whenever(idempotencyService.findByKey("test-key")).thenReturn(null)
            whenever(redissonClient.getLock(eq("idempotency:test-key"))).thenReturn(lock)
            whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(true)
            whenever(joinPoint.proceed()).thenThrow(RuntimeException("downstream error"))

            assertThatThrownBy {
                aspect.handleIdempotency(joinPoint, idempotent)
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("downstream error")

            verify(lock).unlock()
        }

        @Test
        fun 락을_보유하지_않으면_unlock을_호출하지_않는다() {
            setUpRequest()
            whenever(idempotent.lockTimeoutSeconds).thenReturn(10L)
            whenever(idempotencyService.findByKey("test-key")).thenReturn(null)
            whenever(redissonClient.getLock(eq("idempotency:test-key"))).thenReturn(lock)
            whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
            whenever(lock.isHeldByCurrentThread).thenReturn(false)

            val response = ResponseEntity.ok("result")
            whenever(joinPoint.proceed()).thenReturn(response)

            aspect.handleIdempotency(joinPoint, idempotent)

            verify(lock, never()).unlock()
        }
    }

    @Nested
    @DisplayName("상수")
    inner class Constants {

        @Test
        fun 멱등성_키_헤더_이름이_올바르다() {
            assertThat(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER).isEqualTo("Idempotency-Key")
        }
    }
}
