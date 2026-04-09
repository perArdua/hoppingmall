package com.hoppingmall.dlq.controller

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.dlq.service.DLQQueryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@DisplayName("DLQController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQControllerTest {

    private val dlqCommandService: DLQCommandService = mock()
    private val dlqQueryService: DLQQueryService = mock()
    private val controller = DLQController(dlqCommandService, dlqQueryService)

    @Nested
    @DisplayName("getDLQStats")
    inner class GetDLQStats {

        @Test
        fun DLQ_통계_조회_성공() {
            val expectedStats = mapOf(
                "totalMessages" to 100L,
                "pendingCount" to 50L,
                "processedCount" to 30L,
                "failedCount" to 20L
            )
            whenever(dlqQueryService.getDLQStats()).thenReturn(expectedStats)

            val response = controller.getDLQStats()

            assertEquals("SUCCESS", response.code)
            assertEquals(100L, response.data!!["totalMessages"])
            assertEquals(50L, response.data!!["pendingCount"])
            verify(dlqQueryService).getDLQStats()
        }
    }

    @Nested
    @DisplayName("getDLQMessages")
    inner class GetDLQMessages {

        @Test
        fun 토픽별_DLQ_메시지_조회_성공() {
            val topic = "payment"
            val dlqMessages = listOf(createDLQMessage(topic = topic))
            val expectedPage = PageImpl(dlqMessages, PageRequest.of(0, 20), 1)
            whenever(dlqQueryService.getDLQMessages(topic, 0, 20)).thenReturn(expectedPage)

            val response = controller.getDLQMessages(topic, 0, 20)

            assertEquals("SUCCESS", response.code)
            assertEquals(1, response.data!!.totalElements)
            verify(dlqQueryService).getDLQMessages(topic, 0, 20)
        }
    }

    @Nested
    @DisplayName("getDLQMessagesByStatus")
    inner class GetDLQMessagesByStatus {

        @Test
        fun 상태별_DLQ_메시지_조회_성공() {
            val status = DLQStatus.PENDING
            val dlqMessages = listOf(createDLQMessage(status = status))
            val expectedPage = PageImpl(dlqMessages, PageRequest.of(0, 20), 1)
            whenever(dlqQueryService.getDLQMessagesByStatus(status, 0, 20)).thenReturn(expectedPage)

            val response = controller.getDLQMessagesByStatus(status, 0, 20)

            assertEquals("SUCCESS", response.code)
            assertEquals(1, response.data!!.totalElements)
            verify(dlqQueryService).getDLQMessagesByStatus(status, 0, 20)
        }
    }

    @Nested
    @DisplayName("retryDLQMessage")
    inner class RetryDLQMessage {

        @Test
        fun 개별_DLQ_메시지_재처리_성공() {
            val messageId = 123L
            whenever(dlqCommandService.retryDLQMessage(messageId)).thenReturn(true)

            val response = controller.retryDLQMessage(messageId)

            assertEquals("SUCCESS", response.code)
            assertEquals("DLQ 메시지 재처리 성공: ID 123", response.data)
            verify(dlqCommandService).retryDLQMessage(messageId)
        }

        @Test
        fun 개별_DLQ_메시지_재처리_실패() {
            val messageId = 456L
            whenever(dlqCommandService.retryDLQMessage(messageId)).thenReturn(false)

            val response = controller.retryDLQMessage(messageId)

            assertEquals("SUCCESS", response.code)
            assertEquals("DLQ 메시지 재처리 실패: ID 456", response.data)
            verify(dlqCommandService).retryDLQMessage(messageId)
        }
    }

    @Nested
    @DisplayName("retryDLQMessages")
    inner class RetryDLQMessages {

        @Test
        fun 토픽별_DLQ_메시지_일괄_재처리_성공() {
            val topic = "payment"
            val maxCount = 30
            val expectedResult = mapOf(
                "topic" to topic,
                "totalAttempted" to 10,
                "successCount" to 8,
                "failureCount" to 2
            )
            whenever(dlqCommandService.retryDLQMessagesByTopic(topic, maxCount)).thenReturn(expectedResult)

            val response = controller.retryDLQMessages(topic, maxCount)

            assertEquals("SUCCESS", response.code)
            verify(dlqCommandService).retryDLQMessagesByTopic(topic, maxCount)
        }
    }

    @Nested
    @DisplayName("clearProcessedDLQMessages")
    inner class ClearProcessedDLQMessages {

        @Test
        fun 처리_완료된_DLQ_메시지_삭제_성공() {
            val topic = "payment"
            val deletedCount = 25L
            whenever(dlqCommandService.clearProcessedDLQMessages(topic)).thenReturn(deletedCount)

            val response = controller.clearProcessedDLQMessages(topic)

            assertEquals("SUCCESS", response.code)
            verify(dlqCommandService).clearProcessedDLQMessages(topic)
        }
    }

    private fun createDLQMessage(
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 1000L,
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis(),
        status: DLQStatus = DLQStatus.PENDING
    ): DLQMessage {
        return DLQMessage(
            originalTopic = topic,
            originalPartition = partition,
            originalOffset = offset,
            originalKey = key,
            originalValue = value,
            exceptionMessage = exception,
            errorTimestamp = timestamp
        ).apply { this.status = status }
    }
}
