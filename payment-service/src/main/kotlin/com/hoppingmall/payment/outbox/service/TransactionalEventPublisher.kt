package com.hoppingmall.payment.outbox.service

import com.hoppingmall.outbox.service.OutboxEventWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionalEventPublisher(
    private val outboxEventWriter: OutboxEventWriter
) {
    @Transactional(rollbackFor = [Exception::class])
    fun publishEvent(
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
}
