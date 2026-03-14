package com.hoppingmall.order.port

interface TransactionalEventPublisherPort {
    fun publishEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Map<String, Any>,
        topic: String,
        partitionKey: String
    )
}
