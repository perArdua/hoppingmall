package com.hoppingmall.payment.dlq.service

import com.hoppingmall.payment.dlq.domain.DLQMessage
import com.hoppingmall.payment.dlq.domain.DLQStatus
import com.hoppingmall.payment.dlq.domain.repository.DLQMessageRepository
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

    fun getDLQStats(): Map<String, Any> {
        val totalCount = dlqMessageRepository.count()
        val pendingCount = dlqMessageRepository.countByStatus(DLQStatus.PENDING)
        val processedCount = dlqMessageRepository.countByStatus(DLQStatus.PROCESSED)
        val failedCount = dlqMessageRepository.countByStatus(DLQStatus.FAILED)

        val topicStats = dlqMessageRepository.getDLQStatsByTopic()

        return mapOf(
            "totalMessages" to totalCount,
            "pendingCount" to pendingCount,
            "processedCount" to processedCount,
            "failedCount" to failedCount,
            "topicStats" to topicStats.map { stats ->
                mapOf(
                    "topic" to stats.topic,
                    "total" to stats.totalCount,
                    "pending" to stats.pendingCount,
                    "processed" to stats.processedCount,
                    "failed" to stats.failedCount
                )
            },
            "lastUpdated" to System.currentTimeMillis()
        )
    }
}
