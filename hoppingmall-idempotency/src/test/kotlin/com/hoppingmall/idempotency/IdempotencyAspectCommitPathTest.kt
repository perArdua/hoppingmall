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
import org.mockito.Mockito.lenient
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
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@DisplayName("IdempotencyAspect 트랜잭션 안 기록·커밋 선점 경로")
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

    private fun record(key: String, body: String) = IdempotencyRecord(
        idempotencyKey = key,
        httpMethod = "POST",
        endpoint = "/api/v1/orders",
        responseStatus = 201,
        responseBody = body,
        expiresAt = LocalDateTime.now().plusHours(1)
    )

    @Nested
    @DisplayName("기본값")
    inner class Defaults {

        @Test
        fun 보관함을_열되_선점_보관함은_아니고_락을_잡는다() {
            stubLock()
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            var opened: PendingIdempotency? = null
            var claimKey: String? = "unset"
            whenever(joinPoint.proceed()).thenAnswer {
                opened = IdempotencyRequestContext.current()
                claimKey = IdempotencyRequestContext.claimKey()
                ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to 1))
            }

            aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(opened).isNotNull
            assertThat(opened!!.claimByCommit).isFalse()
            assertThat(claimKey).isNull()
            assertThat(IdempotencyRequestContext.current()).isNull()
            verify(lock).unlock()
            verify(idempotencyService).save(any(), eq("POST"), eq("/api/v1/orders"), eq(201), any(), any(), eq(24L))
        }

        @Test
        fun 커밋_선점으로_표시된_엔드포인트도_락을_잡고_선점_보관함을_열지_않는다() {
            stubLock()
            lenient().whenever(idempotent.claimByCommit).thenReturn(true)
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            var opened: PendingIdempotency? = null
            var claimKey: String? = "unset"
            whenever(joinPoint.proceed()).thenAnswer {
                opened = IdempotencyRequestContext.current()
                claimKey = IdempotencyRequestContext.claimKey()
                ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to 1))
            }

            aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(opened!!.claimByCommit).isFalse()
            assertThat(claimKey).isNull()
            assertThat(IdempotencyRequestContext.current()).isNull()
            verify(lock).unlock()
            verify(idempotencyService).save(any(), eq("POST"), eq("/api/v1/orders"), eq(201), any(), any(), eq(24L))
        }
    }

    @Nested
    @DisplayName("플래그가 모두 꺼짐")
    inner class FlagsOff {

        @BeforeEach
        fun disable() {
            ReflectionTestUtils.setField(aspect, "saveInTx", false)
            ReflectionTestUtils.setField(aspect, "claimByCommit", false)
        }

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
            assertThat(opened!!.claimByCommit).isFalse()
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

    @Nested
    @DisplayName("claim-by-commit 켬")
    inner class ClaimByCommit {

        @BeforeEach
        fun enable() {
            ReflectionTestUtils.setField(aspect, "saveInTx", false)
            ReflectionTestUtils.setField(aspect, "claimByCommit", true)
        }

        @Test
        fun 표시된_엔드포인트는_락_없이_진행하고_선점_보관함을_연다() {
            whenever(idempotent.claimByCommit).thenReturn(true)
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            val response = ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to 3))
            var opened: PendingIdempotency? = null
            whenever(joinPoint.proceed()).thenAnswer {
                opened = IdempotencyRequestContext.current()
                opened!!.savedInTransaction = true
                response
            }

            val result = aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(result).isEqualTo(response)
            assertThat(opened!!.claimByCommit).isTrue()
            assertThat(opened!!.key).hasSize(64)
            assertThat(IdempotencyRequestContext.current()).isNull()
            verify(redissonClient, never()).getLock(any<String>())
            verify(idempotencyService, never()).save(any(), any(), any(), any(), any(), any(), any())
        }

        @Test
        fun 진행_중_예외가_났는데_먼저_커밋된_기록이_있으면_그_응답을_돌려준다() {
            whenever(idempotent.claimByCommit).thenReturn(true)
            whenever(idempotent.ttlHours).thenReturn(24L)
            var claimKey: String? = null
            whenever(joinPoint.proceed()).thenAnswer {
                claimKey = IdempotencyRequestContext.claimKey()
                throw IllegalStateException("duplicate")
            }
            whenever(idempotencyService.findByKey(any()))
                .thenReturn(null)
                .thenAnswer { record(claimKey!!, """{"id":9}""") }

            @Suppress("UNCHECKED_CAST")
            val result = aspect.handleIdempotency(joinPoint, idempotent) as ResponseEntity<Any>

            assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(result.body).isEqualTo(mapOf("id" to 9))
            assertThat(IdempotencyRequestContext.current()).isNull()
            verify(redissonClient, never()).getLock(any<String>())
        }

        @Test
        fun 진행_중_예외가_났고_커밋된_기록이_없으면_예외를_그대로_던진다() {
            whenever(idempotent.claimByCommit).thenReturn(true)
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            whenever(joinPoint.proceed()).thenThrow(IllegalStateException("out of stock"))

            assertThatThrownBy { aspect.handleIdempotency(joinPoint, idempotent) }.hasMessage("out of stock")
            assertThat(IdempotencyRequestContext.current()).isNull()
        }

        @Test
        fun 진행_중_저장하지_않은_성공_응답은_별도_트랜잭션으로_저장한다() {
            whenever(idempotent.claimByCommit).thenReturn(true)
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            whenever(joinPoint.proceed()).thenReturn(ResponseEntity.ok(mapOf("id" to 4)))

            aspect.handleIdempotency(joinPoint, idempotent)

            verify(idempotencyService).save(any(), eq("POST"), eq("/api/v1/orders"), eq(200), any(), any(), eq(24L))
        }

        @Test
        fun 표시되지_않은_엔드포인트는_락_경로를_그대로_탄다() {
            stubLock()
            whenever(idempotent.claimByCommit).thenReturn(false)
            whenever(idempotent.ttlHours).thenReturn(24L)
            whenever(idempotencyService.findByKey(any())).thenReturn(null)
            var opened: PendingIdempotency? = null
            whenever(joinPoint.proceed()).thenAnswer {
                opened = IdempotencyRequestContext.current()
                ResponseEntity.ok(mapOf("id" to 5))
            }

            aspect.handleIdempotency(joinPoint, idempotent)

            assertThat(opened).isNull()
            verify(lock).unlock()
            verify(idempotencyService).save(any(), any(), any(), any(), any(), any(), any())
        }
    }
}
