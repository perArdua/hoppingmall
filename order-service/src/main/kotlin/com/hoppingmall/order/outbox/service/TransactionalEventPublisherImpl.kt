package com.hoppingmall.order.outbox.service

import com.hoppingmall.order.port.TransactionalEventPublisherPort
import com.hoppingmall.outbox.service.OutboxEventWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionalEventPublisherImpl(
    private val outboxEventWriter: OutboxEventWriter
) : TransactionalEventPublisherPort {

    @Transactional(rollbackFor = [Exception::class])
    override fun publishEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Map<String, Any>,
        topic: String,
        partitionKey: String
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
