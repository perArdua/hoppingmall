package com.hoppingmall.idempotency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("IdempotencyService")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class IdempotencyServiceTest {

    @Mock
    private lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @InjectMocks
    private lateinit var idempotencyService: IdempotencyService

    @Nested
    @DisplayName("findByKey")
    inner class FindByKey {

        @Test
        fun 키가_존재하고_만료되지_않았으면_레코드를_반환한다() {
            val record = IdempotencyRecord(
                idempotencyKey = "test-key",
                httpMethod = "POST",
                endpoint = "/api/v1/orders",
                responseStatus = 200,
                responseBody = """{"id":1}""",
                expiresAt = LocalDateTime.now().plusHours(1)
            )
            whenever(idempotencyRecordRepository.findByIdempotencyKey("test-key"))
                .thenReturn(Optional.of(record))

            val result = idempotencyService.findByKey("test-key")

            assertThat(result).isEqualTo(record)
        }

        @Test
        fun 키가_존재하지만_만료되었으면_null을_반환한다() {
            val expiredRecord = IdempotencyRecord(
                idempotencyKey = "test-key",
                httpMethod = "POST",
                endpoint = "/api/v1/orders",
                responseStatus = 200,
                responseBody = """{"id":1}""",
                expiresAt = LocalDateTime.now().minusHours(1)
            )
            whenever(idempotencyRecordRepository.findByIdempotencyKey("test-key"))
                .thenReturn(Optional.of(expiredRecord))

            val result = idempotencyService.findByKey("test-key")

            assertThat(result).isNull()
        }

        @Test
        fun 키가_존재하지_않으면_null을_반환한다() {
            whenever(idempotencyRecordRepository.findByIdempotencyKey("missing-key"))
                .thenReturn(Optional.empty())

            val result = idempotencyService.findByKey("missing-key")

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("save")
    inner class Save {

        @Test
        fun 레코드를_정상적으로_저장한다() {
            val expectedRecord = IdempotencyRecord(
                id = 1L,
                idempotencyKey = "save-key",
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 201,
                responseBody = """{"status":"ok"}""",
                expiresAt = LocalDateTime.now().plusHours(24)
            )
            whenever(idempotencyRecordRepository.save(any<IdempotencyRecord>()))
                .thenReturn(expectedRecord)

            val result = idempotencyService.save(
                key = "save-key",
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 201,
                responseBody = """{"status":"ok"}""",
                ttlHours = 24
            )

            assertThat(result).isEqualTo(expectedRecord)
        }

        @Test
        fun 저장할_때_올바른_필드로_레코드를_생성한다() {
            val captor = argumentCaptor<IdempotencyRecord>()
            val savedRecord = IdempotencyRecord(
                id = 1L,
                idempotencyKey = "cap-key",
                httpMethod = "PUT",
                endpoint = "/api/v1/items/1",
                responseStatus = 200,
                responseBody = """{}""",
                expiresAt = LocalDateTime.now().plusHours(12)
            )
            whenever(idempotencyRecordRepository.save(captor.capture()))
                .thenReturn(savedRecord)

            idempotencyService.save(
                key = "cap-key",
                httpMethod = "PUT",
                endpoint = "/api/v1/items/1",
                responseStatus = 200,
                responseBody = """{}""",
                ttlHours = 12
            )

            val captured = captor.firstValue
            assertThat(captured.idempotencyKey).isEqualTo("cap-key")
            assertThat(captured.httpMethod).isEqualTo("PUT")
            assertThat(captured.endpoint).isEqualTo("/api/v1/items/1")
            assertThat(captured.responseStatus).isEqualTo(200)
            assertThat(captured.responseBody).isEqualTo("""{}""")
            assertThat(captured.expiresAt).isAfter(LocalDateTime.now())
        }

        @Test
        fun TTL이_0이면_즉시_만료되는_레코드를_생성한다() {
            val captor = argumentCaptor<IdempotencyRecord>()
            val savedRecord = IdempotencyRecord(
                idempotencyKey = "zero-ttl",
                httpMethod = "POST",
                endpoint = "/api/test",
                responseStatus = 200,
                responseBody = """{}""",
                expiresAt = LocalDateTime.now()
            )
            whenever(idempotencyRecordRepository.save(captor.capture()))
                .thenReturn(savedRecord)

            idempotencyService.save(
                key = "zero-ttl",
                httpMethod = "POST",
                endpoint = "/api/test",
                responseStatus = 200,
                responseBody = """{}""",
                ttlHours = 0
            )

            val captured = captor.firstValue
            assertThat(captured.expiresAt).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1))
        }
    }

    @Nested
    @DisplayName("cleanupExpiredRecords")
    inner class CleanupExpiredRecords {

        @Test
        fun 만료된_레코드가_있으면_삭제한다() {
            whenever(idempotencyRecordRepository.deleteExpiredRecords(any()))
                .thenReturn(5)

            idempotencyService.cleanupExpiredRecords()

            verify(idempotencyRecordRepository).deleteExpiredRecords(any())
        }

        @Test
        fun 만료된_레코드가_없어도_삭제를_호출한다() {
            whenever(idempotencyRecordRepository.deleteExpiredRecords(any()))
                .thenReturn(0)

            idempotencyService.cleanupExpiredRecords()

            verify(idempotencyRecordRepository).deleteExpiredRecords(any())
        }
    }
}
