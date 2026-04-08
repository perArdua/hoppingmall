package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.domain.repository.DLQStatsProjection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@DisplayName("DLQQueryService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQQueryServiceTest {

    private val dlqMessageRepository: DLQMessageRepository = mock()
    private val dlqQueryService = DLQQueryService(dlqMessageRepository)

    @Nested
    @DisplayName("getDLQMessages")
    inner class GetDLQMessages {

        @Test
        fun 토픽별_DLQ_메시지를_페이징하여_조회한다() {
            val topic = "payment"
            val page = 0
            val size = 20
            val expectedMessages = listOf(createDLQMessage(topic = topic))
            val expectedPage = PageImpl(expectedMessages, PageRequest.of(page, size), 1)

            whenever(dlqMessageRepository.findByOriginalTopicOrderByCreatedAtDesc(
                eq(topic), any()
            )).thenReturn(expectedPage)

            val result = dlqQueryService.getDLQMessages(topic, page, size)

            assertEquals(expectedPage, result)
            verify(dlqMessageRepository).findByOriginalTopicOrderByCreatedAtDesc(
                eq(topic), eq(PageRequest.of(page, size))
            )
        }
    }

    @Nested
    @DisplayName("getDLQMessagesByStatus")
    inner class GetDLQMessagesByStatus {

        @Test
        fun 상태별_DLQ_메시지를_페이징하여_조회한다() {
            val status = DLQStatus.PENDING
            val page = 0
            val size = 20
            val expectedMessages = listOf(createDLQMessage(status = status))
            val expectedPage = PageImpl(expectedMessages, PageRequest.of(page, size), 1)

            whenever(dlqMessageRepository.findByStatusOrderByCreatedAtDesc(
                eq(status), any()
            )).thenReturn(expectedPage)

            val result = dlqQueryService.getDLQMessagesByStatus(status, page, size)

            assertEquals(expectedPage, result)
            verify(dlqMessageRepository).findByStatusOrderByCreatedAtDesc(
                eq(status), eq(PageRequest.of(page, size))
            )
        }
    }

    @Nested
    @DisplayName("getDLQStats")
    inner class GetDLQStats {

        @Test
        fun DLQ_통계를_조회한다() {
            whenever(dlqMessageRepository.count()).thenReturn(10)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.PENDING)).thenReturn(5)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.PROCESSED)).thenReturn(3)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.FAILED)).thenReturn(2)
            whenever(dlqMessageRepository.getDLQStatsByTopic()).thenReturn(emptyList())

            val stats = dlqQueryService.getDLQStats()

            assertEquals(10L, stats["totalMessages"])
            assertEquals(5L, stats["pendingCount"])
            assertEquals(3L, stats["processedCount"])
            assertEquals(2L, stats["failedCount"])
            assertNotNull(stats["lastUpdated"])
            assertNotNull(stats["topicStats"])
        }

        @Test
        fun 토픽별_통계를_포함하여_조회한다() {
            whenever(dlqMessageRepository.count()).thenReturn(5)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.PENDING)).thenReturn(3)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.PROCESSED)).thenReturn(1)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.FAILED)).thenReturn(1)

            val topicStats = listOf(
                object : DLQStatsProjection {
                    override val topic = "payment"
                    override val totalCount = 3L
                    override val pendingCount = 2L
                    override val processedCount = 1L
                    override val failedCount = 0L
                },
                object : DLQStatsProjection {
                    override val topic = "order"
                    override val totalCount = 2L
                    override val pendingCount = 1L
                    override val processedCount = 0L
                    override val failedCount = 1L
                }
            )
            whenever(dlqMessageRepository.getDLQStatsByTopic()).thenReturn(topicStats)

            val stats = dlqQueryService.getDLQStats()

            @Suppress("UNCHECKED_CAST")
            val resultTopicStats = stats["topicStats"] as List<Map<String, Any>>
            assertEquals(2, resultTopicStats.size)
            assertEquals("payment", resultTopicStats[0]["topic"])
            assertEquals(3L, resultTopicStats[0]["total"])
            assertEquals("order", resultTopicStats[1]["topic"])
            assertEquals(2L, resultTopicStats[1]["total"])
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
