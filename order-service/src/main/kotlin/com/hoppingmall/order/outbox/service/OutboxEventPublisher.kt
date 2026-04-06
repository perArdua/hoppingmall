package com.hoppingmall.order.outbox.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    private val outboxMetrics: OutboxMetrics,
    private val avroEventConverter: AvroEventConverter
) {

    private val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java)
    private val maxRetries = 3

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun publishPendingEvents() {
        val pendingEvents = outboxEventRepository.findUnprocessedEvents(
            status = OutboxStatus.PENDING,
            retryStatus = OutboxStatus.FAILED,
            maxRetries = maxRetries,
            limit = 100
        )

        if (pendingEvents.isNotEmpty()) {
            logger.info("Processing ${pendingEvents.size} pending outbox events")

            pendingEvents.forEach { event ->
                val eventId = event.id ?: return@forEach
                val claimed = outboxEventRepository.claimEventForPublish(
                    id = eventId,
                    nextStatus = OutboxStatus.RETRYING,
                    updatedAt = LocalDateTime.now(),
                    pendingStatus = OutboxStatus.PENDING,
                    failedStatus = OutboxStatus.FAILED,
                    maxRetries = maxRetries
                )
                if (claimed > 0) {
                    publishEvent(eventId)
                }
            }
        }
    }

    @Transactional
    fun publishEvent(eventId: Long) {
        var event: OutboxEvent? = null
        try {
            event = outboxEventRepository.findById(eventId).orElse(null) ?: return
            if (event.processed || event.status != OutboxStatus.RETRYING) {
                return
            }

            val avroRecord = avroEventConverter.convertJsonToAvro(event.eventType, event.eventData)

            val result = kafkaTemplate.executeInTransaction { template ->
                template.send(
                    event.topic,
                    event.partitionKey ?: "",
                    avroRecord
                ).get(10, java.util.concurrent.TimeUnit.SECONDS)
            }

            handlePublishSuccess(event, result)

        } catch (e: Exception) {
            val failedEvent = event
            if (failedEvent != null) {
                logger.error("Failed to publish outbox event: ${failedEvent.id}", e)
                handlePublishFailure(failedEvent, e)
            } else {
                logger.error("Failed to publish outbox event: $eventId", e)
            }
        }
    }

    private fun handlePublishSuccess(event: OutboxEvent, result: SendResult<String, Any>) {
        event.markAsProcessed()
        outboxEventRepository.save(event)
        outboxMetrics.recordOutboxPublished(event.topic)
        outboxMetrics.recordPublishLatency(event.createdAt)

        logger.info("Outbox event published successfully: " +
            "eventId=${event.id}, topic=${event.topic}, " +
            "offset=${result.recordMetadata.offset()}")
    }

    private fun handlePublishFailure(event: OutboxEvent, throwable: Throwable) {
        val errorMessage = throwable.message ?: "Unknown error"
        val willExceedMaxRetry = event.retryCount + 1 >= maxRetries

        if (willExceedMaxRetry) {
            event.markAsFailedPermanently("Max retries exceeded: $errorMessage")
            logger.error("Outbox event failed after max retries: eventId=${event.id}, error=$errorMessage")
        } else {
            event.markAsFailed(errorMessage)
            logger.warn("Outbox event failed, will retry: eventId=${event.id}, " +
                "retryCount=${event.retryCount}, error=$errorMessage")
        }

        outboxMetrics.recordOutboxFailed(event.topic)
        outboxEventRepository.save(event)
    }
}
