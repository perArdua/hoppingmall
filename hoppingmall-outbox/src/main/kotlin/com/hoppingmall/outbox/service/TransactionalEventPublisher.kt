package com.hoppingmall.outbox.service

interface TransactionalEventPublisher {
    fun publishEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Any,
        topic: String,
        partitionKey: String? = null
    )
}
