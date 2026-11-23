package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@DisplayName("IdempotencyTxRecorder")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class IdempotencyTxRecorderTest {

    @Mock
    private lateinit var idempotencyService: IdempotencyService

    private lateinit var recorder: IdempotencyTxRecorder

    @BeforeEach
    fun setUp() {
        recorder = IdempotencyTxRecorder(idempotencyService, ObjectMapper())
    }

    @AfterEach
    fun tearDown() {
        IdempotencyRequestContext.clear()
    }

    @Test
    fun 보관된_멱등_정보가_없으면_응답을_만들지도_저장하지도_않는다() {
        var built = false

        recorder.recordIfPending {
            built = true
            ResponseEntity.ok("x")
        }

        assertThat(built).isFalse()
        verify(idempotencyService, never()).saveInCurrentTransaction(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun 성공_응답이면_지문과_함께_현재_트랜잭션에_저장하고_저장됨으로_표시한다() {
        val pending = PendingIdempotency("k1", "POST", "/api/v1/orders", "fp-1", 24L)
        IdempotencyRequestContext.open(pending)

        recorder.recordIfPending { ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to 7)) }

        verify(idempotencyService).saveInCurrentTransaction(
            eq("k1"), eq("POST"), eq("/api/v1/orders"), eq(201), eq("""{"id":7}"""), eq("fp-1"), eq(24L)
        )
        assertThat(pending.savedInTransaction).isTrue()
    }

    @Test
    fun 성공이_아닌_응답이면_저장하지_않는다() {
        val pending = PendingIdempotency("k2", "POST", "/api/v1/orders", "fp-2", 24L)
        IdempotencyRequestContext.open(pending)

        recorder.recordIfPending { ResponseEntity.badRequest().body("bad") }

        verify(idempotencyService, never()).saveInCurrentTransaction(any(), any(), any(), any(), any(), any(), any())
        assertThat(pending.savedInTransaction).isFalse()
    }

    @Test
    fun 커밋_선점_요청이_아니면_다른_요청이_커밋했는지_조회하지_않는다() {
        IdempotencyRequestContext.open(PendingIdempotency("k3", "POST", "/api/v1/orders", "fp-3", 24L))

        assertThat(recorder.claimedByCommittedRequest()).isFalse()
        IdempotencyRequestContext.clear()
        assertThat(recorder.claimedByCommittedRequest()).isFalse()
        verify(idempotencyService, never()).findByKey(any())
    }

    @Test
    fun 커밋_선점_요청이면_같은_키로_커밋된_기록이_있는지로_판단한다() {
        IdempotencyRequestContext.open(PendingIdempotency("k4", "POST", "/api/v1/orders", "fp-4", 24L, claimByCommit = true))
        whenever(idempotencyService.findByKey("k4"))
            .thenReturn(null)
            .thenReturn(
                IdempotencyRecord(
                    idempotencyKey = "k4",
                    httpMethod = "POST",
                    endpoint = "/api/v1/orders",
                    responseStatus = 201,
                    responseBody = "{}",
                    expiresAt = LocalDateTime.now().plusHours(1)
                )
            )

        assertThat(recorder.claimedByCommittedRequest()).isFalse()
        assertThat(recorder.claimedByCommittedRequest()).isTrue()
        assertThat(IdempotencyRequestContext.claimKey()).isEqualTo("k4")
    }
}
