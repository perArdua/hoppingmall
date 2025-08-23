package com.hoppingmall.mall.global.common.controller

import com.hoppingmall.mall.global.common.domain.DLQMessage
import com.hoppingmall.mall.global.common.domain.DLQStatus
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.common.service.DLQService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@DisplayName("DLQController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQControllerTest {

    private val dlqService: DLQService = mock()
    private val controller = DLQController(dlqService)

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

            whenever(dlqService.getDLQStats()).thenReturn(expectedStats)

            val response: ApiResponse<Map<String, Any>> = controller.getDLQStats()

            kotlin.test.assertEquals("SUCCESS", response.code)
            kotlin.test.assertEquals(100L, (response.data!!["totalMessages"]))
            kotlin.test.assertEquals(50L, (response.data!!["pendingCount"]))
            verify(dlqService).getDLQStats()
        }
    }

    @Nested
    @DisplayName("getDLQMessagesByTopic")
    inner class GetDLQMessagesByTopic {
        @Test
        fun 토픽별_DLQ_메시지_조회_성공() {
            val topic = "payment"
            val dlqMessages = listOf(createDLQMessage(topic = topic))
            val expectedPage = PageImpl(dlqMessages, PageRequest.of(0, 20), 1)

            whenever(dlqService.getDLQMessages(topic, 0, 20)).thenReturn(expectedPage)

            val response = controller.getDLQMessages(topic, 0, 20)

            kotlin.test.assertEquals("SUCCESS", response.code)
            kotlin.test.assertEquals(1, response.data!!.totalElements)
            verify(dlqService).getDLQMessages(topic, 0, 20)
        }
    }

    @Nested
    @DisplayName("retryDLQMessage")
    inner class RetryDLQMessage {
        @Test
        fun 개별_DLQ_메시지_재처리_성공() {
            val messageId = 123L
            whenever(dlqService.retryDLQMessage(messageId)).thenReturn(true)

            val response = controller.retryDLQMessage(messageId)

            kotlin.test.assertEquals("SUCCESS", response.code)
            verify(dlqService).retryDLQMessage(messageId)
        }
    }

    @Nested
    @DisplayName("retryDLQMessagesByTopic")
    inner class RetryDLQMessagesByTopic {
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

            whenever(dlqService.retryDLQMessagesByTopic(topic, maxCount)).thenReturn(expectedResult)

            val response = controller.retryDLQMessages(topic, maxCount)

            kotlin.test.assertEquals("SUCCESS", response.code)
            verify(dlqService).retryDLQMessagesByTopic(topic, maxCount)
        }
    }

    @Nested
    @DisplayName("clearProcessedDLQMessages")
    inner class ClearProcessedDLQMessages {
        @Test
        fun 처리_완료된_DLQ_메시지_삭제_성공() {
            val topic = "payment"
            val deletedCount = 25L
            whenever(dlqService.clearProcessedDLQMessages(topic)).thenReturn(deletedCount)

            val response = controller.clearProcessedDLQMessages(topic)

            kotlin.test.assertEquals("SUCCESS", response.code)
            verify(dlqService).clearProcessedDLQMessages(topic)
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

            whenever(dlqService.getDLQMessagesByStatus(status, 0, 20)).thenReturn(expectedPage)

            val response = controller.getDLQMessagesByStatus(status, 0, 20)

            kotlin.test.assertEquals("SUCCESS", response.code)
            kotlin.test.assertEquals(1, response.data!!.totalElements)
            verify(dlqService).getDLQMessagesByStatus(status, 0, 20)
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
