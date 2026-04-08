package com.hoppingmall.order.outbox.service

import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.outbox.service.OutboxEventWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionalEventPublisherImpl(
    private val outboxEventWriter: OutboxEventWriter
) : TransactionalEventPublisher {

    @Transactional(rollbackFor = [Exception::class])
    override fun publishEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Any,
        topic: String,
        partitionKey: String?
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
