package com.hoppingmall.outbox.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxEventWriter(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(OutboxEventWriter::class.java)

    @Transactional
    fun saveEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Any,
        topic: String,
        partitionKey: String? = null
    ) {
        val serializedData = objectMapper.writeValueAsString(eventData)
        val duplicated = outboxEventRepository.existsDuplicateEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = serializedData,
            statuses = listOf(OutboxStatus.PENDING, OutboxStatus.RETRYING, OutboxStatus.PUBLISHED)
        )

        if (duplicated) {
            logger.warn("Duplicate outbox event skipped: aggregateId=$aggregateId, eventType=$eventType")
            return
        }

        val outboxEvent = OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = serializedData,
            topic = topic,
            partitionKey = partitionKey ?: aggregateId
        )

        outboxEventRepository.save(outboxEvent)
        logger.debug("Outbox event saved: aggregateId=$aggregateId, eventType=$eventType")
    }
}
