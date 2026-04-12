package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val outboxMetrics: OutboxMetrics,
    private val avroEventConverter: AvroEventConverter,
    transactionManager: PlatformTransactionManager
) {

    private val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java)
    private val maxRetries = 3
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Scheduled(fixedDelayString = "\${outbox.publish.interval-ms:5000}")
    fun publishPendingEvents() {
        val pendingEvents = transactionTemplate.execute {
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = maxRetries,
                limit = 100
            )
        } ?: return

        if (pendingEvents.isNotEmpty()) {
            logger.info("Processing ${pendingEvents.size} pending outbox events")

            pendingEvents.forEach { event ->
                val eventId = event.id ?: return@forEach
                val claimed = transactionTemplate.execute {
                    outboxEventRepository.claimEventForPublish(
                        id = eventId,
                        nextStatus = OutboxStatus.RETRYING,
                        updatedAt = LocalDateTime.now(),
                        pendingStatus = OutboxStatus.PENDING,
                        failedStatus = OutboxStatus.FAILED,
                        maxRetries = maxRetries
                    )
                } ?: 0
                if (claimed > 0) {
                    publishEvent(eventId)
                }
            }
        }
    }

    fun publishEvent(eventId: Long) {
        val event = transactionTemplate.execute {
            outboxEventRepository.findByIdOrNull(eventId)
        } ?: return

        if (event.processed || event.status != OutboxStatus.RETRYING) {
            return
        }

        try {
            val avroRecord = avroEventConverter.convertJsonToAvro(event.eventType, event.eventData)

            val result = kafkaTemplate.executeInTransaction { template ->
                template.send(
                    event.topic,
                    event.partitionKey ?: "",
                    avroRecord
                ).get(10, TimeUnit.SECONDS)
            }

            transactionTemplate.executeWithoutResult {
                handlePublishSuccess(event, result)
            }

        } catch (e: Exception) {
            logger.error("Failed to publish outbox event: ${event.id}", e)
            transactionTemplate.executeWithoutResult {
                handlePublishFailure(event, e)
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
