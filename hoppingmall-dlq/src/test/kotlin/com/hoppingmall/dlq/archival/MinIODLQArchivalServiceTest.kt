package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception

@DisplayName("MinIODLQArchivalService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MinIODLQArchivalServiceTest {

    private val s3Client: S3Client = mock()
    private val properties = DLQArchivalProperties(
        enabled = true,
        bucket = "test-bucket",
        endpoint = "http://localhost:9000",
        accessKey = "minioadmin",
        secretKey = "minioadmin"
    )
    private val archivalService = MinIODLQArchivalService(s3Client, properties)

    @Nested
    @DisplayName("archive")
    inner class Archive {

        @Test
        fun DLQ_메시지를_S3에_아카이빙한다() {
            val dlqMessage = createDLQMessage(id = 1L, status = DLQStatus.FAILED)
            whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()))
                .thenReturn(PutObjectResponse.builder().build())

            val result = archivalService.archive(dlqMessage)

            assertTrue(result)
            verify(s3Client).putObject(
                argThat<PutObjectRequest> { bucket() == "test-bucket" && contentType() == "application/json" },
                any<RequestBody>()
            )
        }

        @Test
        fun S3_오류_시_false를_반환한다() {
            val dlqMessage = createDLQMessage(id = 1L, status = DLQStatus.FAILED)
            whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()))
                .thenThrow(S3Exception.builder().message("Connection refused").build())

            val result = archivalService.archive(dlqMessage)

            assertFalse(result)
        }

        @Test
        fun originalValue가_null이어도_아카이빙에_성공한다() {
            val dlqMessage = createDLQMessage(id = 1L, value = null, status = DLQStatus.FAILED)
            whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()))
                .thenReturn(PutObjectResponse.builder().build())

            val result = archivalService.archive(dlqMessage)

            assertTrue(result)
        }

        @Test
        @Suppress("UNCHECKED_CAST")
        fun 아카이빙된_JSON에_모든_필드가_포함된다() {
            val dlqMessage = createDLQMessage(
                id = 1L,
                topic = "payment",
                partition = 2,
                offset = 500L,
                key = "pay-key",
                value = """{"orderId": 123}""",
                exception = "TimeoutException",
                status = DLQStatus.FAILED,
                retryCount = 3
            )

            var capturedBody: String? = null
            whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())).thenAnswer { invocation ->
                val body = invocation.getArgument<RequestBody>(1)
                capturedBody = body.contentStreamProvider().newStream().bufferedReader().readText()
                PutObjectResponse.builder().build()
            }

            archivalService.archive(dlqMessage)

            assertNotNull(capturedBody)
            val parsed = ObjectMapper().readValue(capturedBody, Map::class.java) as Map<String, Any?>
            assertEquals("payment", parsed["originalTopic"])
            assertEquals(2, parsed["originalPartition"])
            assertEquals(500, parsed["originalOffset"])
            assertEquals("pay-key", parsed["originalKey"])
            assertEquals("{\"orderId\": 123}", parsed["originalValue"])
            assertEquals("TimeoutException", parsed["exceptionMessage"])
            assertEquals("FAILED", parsed["status"])
            assertEquals(3, parsed["retryCount"])
            assertNotNull(parsed["createdAt"])
            assertNotNull(parsed["archivedAt"])
        }
    }

    @Nested
    @DisplayName("archiveBatch")
    inner class ArchiveBatch {

        @Test
        fun 여러_메시지를_배치로_아카이빙한다() {
            val messages = listOf(
                createDLQMessage(id = 1L),
                createDLQMessage(id = 2L),
                createDLQMessage(id = 3L)
            )
            whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()))
                .thenReturn(PutObjectResponse.builder().build())

            val result = archivalService.archiveBatch(messages)

            assertEquals(3, result)
            verify(s3Client, times(3)).putObject(any<PutObjectRequest>(), any<RequestBody>())
        }

        @Test
        fun 일부_실패_시_성공_건수만_반환한다() {
            val messages = listOf(
                createDLQMessage(id = 1L),
                createDLQMessage(id = 2L),
                createDLQMessage(id = 3L)
            )
            whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()))
                .thenReturn(PutObjectResponse.builder().build())
                .thenThrow(S3Exception.builder().message("Error").build())
                .thenReturn(PutObjectResponse.builder().build())

            val result = archivalService.archiveBatch(messages)

            assertEquals(2, result)
        }

        @Test
        fun 빈_리스트는_0을_반환한다() {
            val result = archivalService.archiveBatch(emptyList())

            assertEquals(0, result)
            verify(s3Client, never()).putObject(any<PutObjectRequest>(), any<RequestBody>())
        }
    }

    private fun createDLQMessage(
        id: Long? = 1L,
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 1000L,
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis(),
        status: DLQStatus = DLQStatus.FAILED,
        retryCount: Int = 0
    ): DLQMessage {
        val dlqMessage = DLQMessage(
            originalTopic = topic,
            originalPartition = partition,
            originalOffset = offset,
            originalKey = key,
            originalValue = value,
            exceptionMessage = exception,
            errorTimestamp = timestamp
        ).apply {
            this.status = status
            this.retryCount = retryCount
        }

        id?.let {
            val idField = DLQMessage::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(dlqMessage, it)
        }

        return dlqMessage
    }
}
