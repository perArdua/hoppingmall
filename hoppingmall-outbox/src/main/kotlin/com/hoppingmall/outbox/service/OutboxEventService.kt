package com.hoppingmall.outbox.service

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxEventService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventWriter: OutboxEventWriter
) {

    @Transactional
    fun saveEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Any,
        topic: String,
        partitionKey: String? = null
    ) {
        outboxEventWriter.saveEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = eventData,
            topic = topic,
            partitionKey = partitionKey
        )
    }

    fun getEventsByAggregateId(aggregateId: String, aggregateType: String): List<OutboxEvent> {
        return outboxEventRepository.findByAggregateIdAndType(aggregateId, aggregateType)
    }

    fun getEventStats(): Map<String, Long> {
        return mapOf(
            "pending" to outboxEventRepository.countByStatus(OutboxStatus.PENDING),
            "retrying" to outboxEventRepository.countByStatus(OutboxStatus.RETRYING),
            "published" to outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED),
            "failed" to outboxEventRepository.countByStatus(OutboxStatus.FAILED)
        )
    }
}
