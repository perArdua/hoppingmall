package com.hoppingmall.mall.global.idempotency

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("IdempotencyService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IdempotencyServiceTest {

    private val idempotencyRecordRepository: IdempotencyRecordRepository = mock()
    private val idempotencyService = IdempotencyService(idempotencyRecordRepository)

    @Nested
    @DisplayName("findByKey")
    inner class FindByKey {

        @Test
        fun 유효한_레코드가_있으면_반환한다() {
            // given
            val key = "test-key-123"
            val record = IdempotencyRecord(
                id = 1L,
                idempotencyKey = key,
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 200,
                responseBody = """{"id":1}""",
                expiresAt = LocalDateTime.now().plusHours(1)
            )

            whenever(idempotencyRecordRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.of(record))

            // when
            val result = idempotencyService.findByKey(key)

            // then
            assertNotNull(result)
            assertEquals(key, result!!.idempotencyKey)
            assertEquals(200, result.responseStatus)
        }

        @Test
        fun 만료된_레코드는_null을_반환한다() {
            // given
            val key = "expired-key"
            val record = IdempotencyRecord(
                id = 1L,
                idempotencyKey = key,
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 200,
                responseBody = """{"id":1}""",
                expiresAt = LocalDateTime.now().minusHours(1)
            )

            whenever(idempotencyRecordRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.of(record))

            // when
            val result = idempotencyService.findByKey(key)

            // then
            assertNull(result)
        }

        @Test
        fun 레코드가_없으면_null을_반환한다() {
            // given
            val key = "nonexistent-key"

            whenever(idempotencyRecordRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty())

            // when
            val result = idempotencyService.findByKey(key)

            // then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("save")
    inner class Save {

        @Test
        fun 멱등성_레코드를_저장한다() {
            // given
            val key = "new-key"
            val captor = argumentCaptor<IdempotencyRecord>()

            whenever(idempotencyRecordRepository.save(captor.capture())).thenAnswer { captor.lastValue }

            // when
            val result = idempotencyService.save(
                key = key,
                httpMethod = "POST",
                endpoint = "/api/v1/payments",
                responseStatus = 200,
                responseBody = """{"id":1}""",
                ttlHours = 24
            )

            // then
            assertEquals(key, result.idempotencyKey)
            assertEquals("POST", result.httpMethod)
            assertEquals("/api/v1/payments", result.endpoint)
            assertEquals(200, result.responseStatus)
            assertTrue(result.expiresAt.isAfter(LocalDateTime.now().plusHours(23)))
        }
    }

    @Nested
    @DisplayName("cleanupExpiredRecords")
    inner class CleanupExpiredRecords {

        @Test
        fun 만료된_레코드를_정리한다() {
            // given
            whenever(idempotencyRecordRepository.deleteExpiredRecords(any()))
                .thenReturn(5)

            // when
            idempotencyService.cleanupExpiredRecords()

            // then
            verify(idempotencyRecordRepository).deleteExpiredRecords(any())
        }
    }
}
