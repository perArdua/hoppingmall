package com.hoppingmall.payment.outbox.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.config.OutboxMetrics
import com.hoppingmall.payment.outbox.domain.OutboxEvent
import com.hoppingmall.payment.outbox.domain.OutboxStatus
import com.hoppingmall.payment.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    private val outboxMetrics: OutboxMetrics
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

            val eventData = objectMapper.readValue(event.eventData, Map::class.java)

            kafkaTemplate.executeInTransaction { template ->
                val future: CompletableFuture<SendResult<String, Any>> = template.send(
                    event.topic,
                    event.partitionKey ?: "",
                    eventData
                )

                future.whenComplete { result, throwable ->
                    if (throwable == null) {
                        handlePublishSuccess(event, result)
                    } else {
                        handlePublishFailure(event, throwable)
                    }
                }

                return@executeInTransaction future
            }

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
