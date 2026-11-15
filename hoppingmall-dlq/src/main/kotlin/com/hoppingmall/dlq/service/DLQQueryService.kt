package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.dto.DLQStatsResponse
import com.hoppingmall.dlq.dto.DLQTopicStatsResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class DLQQueryService(
    private val dlqMessageRepository: DLQMessageRepository
) {

    fun getDLQMessages(topic: String, page: Int = 0, size: Int = 100): Page<DLQMessage> {
        val pageable = PageRequest.of(page, size)
        return dlqMessageRepository.findByOriginalTopicOrderByCreatedAtDesc(topic, pageable)
    }

    fun getDLQMessagesByStatus(status: DLQStatus, page: Int = 0, size: Int = 100): Page<DLQMessage> {
        val pageable = PageRequest.of(page, size)
        return dlqMessageRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
    }

    fun getDLQStats(): DLQStatsResponse {
        val totalCount = dlqMessageRepository.count()
        val pendingCount = dlqMessageRepository.countByStatus(DLQStatus.PENDING)
        val processedCount = dlqMessageRepository.countByStatus(DLQStatus.PROCESSED)
        val failedCount = dlqMessageRepository.countByStatus(DLQStatus.FAILED)

        val topicStats = dlqMessageRepository.getDLQStatsByTopic()

        return DLQStatsResponse(
            totalMessages = totalCount,
            pendingCount = pendingCount,
            processedCount = processedCount,
            failedCount = failedCount,
            topicStats = topicStats.map { stats ->
                DLQTopicStatsResponse(
                    topic = stats.topic,
                    total = stats.totalCount,
                    pending = stats.pendingCount,
                    processed = stats.processedCount,
                    failed = stats.failedCount
                )
            },
            lastUpdated = System.currentTimeMillis()
        )
    }
}
