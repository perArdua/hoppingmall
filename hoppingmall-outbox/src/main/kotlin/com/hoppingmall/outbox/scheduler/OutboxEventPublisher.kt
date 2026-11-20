package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val outboxMetrics: OutboxMetrics,
    private val avroEventConverter: AvroEventConverter,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java)
    private val maxRetries = 3
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val claimTransactionTemplate = TransactionTemplate(transactionManager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }
    private val batchLock = ReentrantLock()
    private val emptyClaim = ClaimedBatch(emptyList(), LocalDateTime.MIN)

    @Value("\${outbox.publish.batch-claim:false}")
    private var batchClaim: Boolean = false

    @Scheduled(fixedDelayString = "\${outbox.publish.interval-ms:5000}")
    fun publishPendingEvents() = batchLock.withLock {
        if (!batchClaim) return@withLock publishBatch()
        while (!Thread.currentThread().isInterrupted) {
            val outcome = publishLockedBatch()
            if (outcome.locked < BATCH_LIMIT || outcome.halted) break
        }
    }

    private fun publishLockedBatch(): BatchOutcome {
        val claim = claimLockedBatch()
        if (claim.ids.isEmpty()) return BatchOutcome(locked = 0, halted = false)

        val claimedEvents = transactionTemplate.execute {
            outboxEventRepository.findAllById(claim.ids)
        }.orEmpty()
            .filter { !it.processed && it.status == OutboxStatus.RETRYING }
            .sortedBy { it.createdAt }

        val preparedEvents = prepareEvents(claimedEvents, claim.claimedAt)
        val halted = !publishPrepared(preparedEvents, claim.claimedAt)
        return BatchOutcome(locked = claim.ids.size, halted = halted)
    }

    private fun claimLockedBatch(): ClaimedBatch =
        claimTransactionTemplate.execute {
            val ids = outboxEventRepository.lockUnprocessedEventIds(
                status = OutboxStatus.PENDING.name,
                retryStatus = OutboxStatus.FAILED.name,
                maxRetries = maxRetries,
                limit = BATCH_LIMIT
            )
            if (ids.isEmpty()) return@execute emptyClaim
            val claimedAt = claimTimestamp()
            val updated = outboxEventRepository.claimEventsForPublish(
                ids = ids,
                nextStatus = OutboxStatus.RETRYING.name,
                updatedAt = claimedAt,
                pendingStatus = OutboxStatus.PENDING.name,
                failedStatus = OutboxStatus.FAILED.name,
                maxRetries = maxRetries
            )
            if (updated != ids.size) {
                logger.warn("Outbox batch claim updated $updated rows for ${ids.size} locked ids")
            }
            ClaimedBatch(ids, claimedAt)
        } ?: emptyClaim

    private fun publishBatch() {
        val pendingEvents = transactionTemplate.execute {
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = maxRetries,
                limit = BATCH_LIMIT
            )
        } ?: return

        if (pendingEvents.isEmpty()) return

        logger.info("Processing ${pendingEvents.size} pending outbox events")

        val claimedEventIds = pendingEvents.mapNotNull { event ->
            val eventId = event.id ?: return@mapNotNull null
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
            if (claimed > 0) eventId else null
        }

        if (claimedEventIds.isEmpty()) return

        val claimedEvents = claimedEventIds.mapNotNull { eventId ->
            transactionTemplate.execute {
                outboxEventRepository.findByIdOrNull(eventId)
            }
        }.filter { !it.processed && it.status == OutboxStatus.RETRYING }

        publishPrepared(prepareEvents(claimedEvents))
    }

    private fun prepareEvents(
        events: List<OutboxEvent>,
        claimedAt: LocalDateTime? = null
    ): List<Pair<OutboxEvent, Any>> {
        val preparedEvents = mutableListOf<Pair<OutboxEvent, Any>>()
        events.forEach { event ->
            try {
                val avroRecord = avroEventConverter.convertJsonToAvro(event.eventType, event.eventData)
                preparedEvents.add(event to avroRecord)
            } catch (e: Exception) {
                logger.error("Failed to convert outbox event to Avro: ${event.id}", e)
                transactionTemplate.executeWithoutResult {
                    recordFailure(event, e, claimedAt)
                }
            }
        }
        return preparedEvents
    }

    private fun publishPrepared(
        preparedEvents: List<Pair<OutboxEvent, Any>>,
        claimedAt: LocalDateTime? = null
    ): Boolean {
        if (preparedEvents.isEmpty()) return true

        try {
            val results = kafkaTemplate.executeInTransaction { template ->
                val futures = preparedEvents.map { (event, avroRecord) ->
                    event to template.send(event.topic, event.partitionKey ?: "", avroRecord)
                }
                futures.map { (event, future) ->
                    event to future.get(10, TimeUnit.SECONDS)
                }
            } ?: return false

            transactionTemplate.executeWithoutResult {
                if (claimedAt == null) {
                    results.forEach { (event, result) -> handlePublishSuccess(event, result) }
                } else {
                    markClaimedPublished(results, claimedAt)
                }
            }
            return true
        } catch (e: Exception) {
            logger.error("Batch publish failed for ${preparedEvents.size} events", e)
            transactionTemplate.executeWithoutResult {
                preparedEvents.forEach { (event, _) ->
                    if (!event.processed) recordFailure(event, e, claimedAt)
                }
            }
            return false
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

    private fun markClaimedPublished(results: List<Pair<OutboxEvent, SendResult<String, Any>>>, claimedAt: LocalDateTime) {
        val now = LocalDateTime.now()
        val updated = outboxEventRepository.markClaimedEventsProcessed(
            ids = results.mapNotNull { it.first.id },
            publishedStatus = OutboxStatus.PUBLISHED.name,
            processedAt = now,
            updatedAt = now,
            claimedStatus = OutboxStatus.RETRYING.name,
            claimedAt = claimedAt
        )
        if (updated < results.size) {
            logger.warn(
                "Outbox completion marked $updated of ${results.size} published events; " +
                    "the rest were re-claimed after claim at $claimedAt: ids=${results.map { it.first.id }}"
            )
        }
        results.forEach { (event, result) ->
            outboxMetrics.recordOutboxPublished(event.topic)
            outboxMetrics.recordPublishLatency(event.createdAt)
            logger.info("Outbox event published successfully: " +
                "eventId=${event.id}, topic=${event.topic}, " +
                "offset=${result.recordMetadata.offset()}")
        }
    }

    private fun recordFailure(event: OutboxEvent, throwable: Throwable, claimedAt: LocalDateTime?) {
        if (claimedAt == null) return handlePublishFailure(event, throwable)
        val eventId = event.id ?: return
        applyFailure(event, throwable)
        val updated = outboxEventRepository.markClaimedEventFailed(
            id = eventId,
            status = event.status,
            errorMessage = event.errorMessage,
            retryCount = event.retryCount,
            processed = event.processed,
            processedAt = event.processedAt,
            updatedAt = event.updatedAt,
            claimedStatus = OutboxStatus.RETRYING,
            claimedAt = claimedAt
        )
        if (updated == 0) {
            logger.warn("Outbox event $eventId was re-claimed after claim at $claimedAt; skipped failure mark")
        }
    }

    private fun handlePublishFailure(event: OutboxEvent, throwable: Throwable) {
        applyFailure(event, throwable)
        outboxEventRepository.save(event)
    }

    private fun applyFailure(event: OutboxEvent, throwable: Throwable) {
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
    }

    private fun claimTimestamp(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)

    private data class ClaimedBatch(val ids: List<Long>, val claimedAt: LocalDateTime)

    private data class BatchOutcome(val locked: Int, val halted: Boolean)

    companion object {
        private const val BATCH_LIMIT = 100
    }
}
