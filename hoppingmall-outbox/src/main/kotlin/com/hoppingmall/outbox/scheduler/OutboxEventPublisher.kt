package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.producer.ProducerConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.SendResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.withLock

@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val outboxMetrics: OutboxMetrics,
    private val avroEventConverter: AvroEventConverter,
    transactionManager: PlatformTransactionManager,
    private val brokerBackoff: OutboxBrokerBackoff,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dataSource: DataSource? = null
) {

    private val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java)
    private val maxRetries = 3
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val claimTransactionTemplate = TransactionTemplate(transactionManager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }
    private val batchLock = ReentrantLock()
    private val emptyClaim = ClaimedBatch(emptyList(), LocalDateTime.MIN)

    @Value("\${outbox.publish.batch-claim:true}")
    private var batchClaim: Boolean = true

    @Value("\${outbox.publish.max-claim-age-ms:300000}")
    private var maxClaimAgeMs: Long = 300_000

    @Value("\${outbox.maintenance.stale-threshold-ms:600000}")
    private var staleThresholdMs: Long = 600_000

    @Value("\${outbox.publish.broker-tx-cleanup-ms:10000}")
    private var brokerTxCleanupMs: Long = 10_000

    @PostConstruct
    fun configureClaimMode() {
        if (batchClaim) {
            val databaseProduct = databaseProductName()
            if (!MYSQL_PRODUCT_NAME.equals(databaseProduct, ignoreCase = true)) {
                batchClaim = false
                logger.info(
                    "Outbox batch claim disabled: connected database is ${databaseProduct ?: "unknown"}, not MySQL; " +
                        "publishing with per-row claims"
                )
            }
        }
        warnIfClaimAgeExceedsStaleWindow()
    }

    private fun databaseProductName(): String? =
        try {
            dataSource?.connection?.use { it.metaData.databaseProductName }
        } catch (e: Exception) {
            logger.warn("Outbox could not read the connected database product name: ${e.message}")
            null
        }

    fun warnIfClaimAgeExceedsStaleWindow() {
        if (!batchClaim) return
        val transactionTimeoutMs = producerTransactionTimeoutMs()
        if (maxClaimAgeMs + transactionTimeoutMs + brokerTxCleanupMs < staleThresholdMs) return
        logger.warn(
            "Outbox claim age guard cannot rule out duplicate publish: " +
                "outbox.publish.max-claim-age-ms($maxClaimAgeMs) + transaction.timeout.ms($transactionTimeoutMs) " +
                "+ outbox.publish.broker-tx-cleanup-ms($brokerTxCleanupMs) " +
                "is not less than outbox.maintenance.stale-threshold-ms($staleThresholdMs)"
        )
    }

    private fun producerTransactionTimeoutMs(): Long {
        val producerFactory: ProducerFactory<String, Any>? = kafkaTemplate.producerFactory
        return producerFactory?.configurationProperties
            ?.get(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)
            ?.toString()
            ?.toLongOrNull()
            ?: ProducerConfig.configDef().defaultValues()
                .getValue(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)
                .toString()
                .toLong()
    }

    @Scheduled(fixedDelayString = "\${outbox.publish.interval-ms:5000}")
    fun publishPendingEvents() = batchLock.withLock {
        if (brokerBackoff.isBackingOff()) return@withLock
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
        val outcome = publishClaimed(preparedEvents, claim.claimedAt)
        return BatchOutcome(locked = claim.ids.size, halted = outcome != PublishOutcome.PUBLISHED)
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

        publishClaimed(prepareEvents(claimedEvents), null)
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

    private fun publishClaimed(preparedEvents: List<Pair<OutboxEvent, Any>>, claimedAt: LocalDateTime?): PublishOutcome {
        val roundStartedAt = LocalDateTime.now(clock)
        return if (brokerBackoff.shouldIsolateEvents()) {
            publishIsolated(preparedEvents, claimedAt, roundStartedAt)
        } else {
            publishPrepared(preparedEvents, claimedAt, BATCH_CLAIM, roundStartedAt)
        }
    }

    private fun publishPrepared(
        preparedEvents: List<Pair<OutboxEvent, Any>>,
        claimedAt: LocalDateTime?,
        claimKind: String,
        roundStartedAt: LocalDateTime
    ): PublishOutcome {
        if (preparedEvents.isEmpty()) return PublishOutcome.PUBLISHED
        return when (val attempt = send(preparedEvents, claimedAt, claimKind)) {
            is SendAttempt.Sent -> {
                markPublished(attempt.results, claimedAt)
                PublishOutcome.PUBLISHED
            }
            is SendAttempt.Failed -> handleSendFailure(preparedEvents, attempt, claimedAt, claimKind, roundStartedAt)
            SendAttempt.Unsent -> {
                releaseClaims(preparedEvents.map { it.first }, claimedAt)
                PublishOutcome.LEFT_CLAIMED
            }
            SendAttempt.Unconfirmed -> PublishOutcome.LEFT_CLAIMED
        }
    }

    private fun send(
        preparedEvents: List<Pair<OutboxEvent, Any>>,
        claimedAt: LocalDateTime?,
        claimKind: String
    ): SendAttempt {
        val results = try {
            kafkaTemplate.executeInTransaction { template ->
                ensureClaimFresh(claimedAt)
                val futures = preparedEvents.map { (event, avroRecord) ->
                    event to template.send(event.topic, event.partitionKey ?: "", avroRecord)
                }
                val sent = futures.map { (event, future) ->
                    event to future.get(10, TimeUnit.SECONDS)
                }
                ensureClaimFresh(claimedAt)
                sent
            } ?: return unconfirmed(preparedEvents)
        } catch (e: ClaimExpiredException) {
            logger.warn(
                "Outbox $claimKind claim expired after ${e.ageMs}ms (limit ${maxClaimAgeMs}ms); " +
                    "aborted Kafka transaction and released ${preparedEvents.size} events to PENDING: " +
                    "ids=${preparedEvents.map { it.first.id }}"
            )
            return SendAttempt.Unsent
        } catch (e: Exception) {
            logger.error("Outbox publish failed for ${preparedEvents.size} events: ids=${preparedEvents.map { it.first.id }}", e)
            return SendAttempt.Failed(e, OutboxFailureClassifier.classify(e))
        }
        return SendAttempt.Sent(results)
    }

    private fun unconfirmed(preparedEvents: List<Pair<OutboxEvent, Any>>): SendAttempt {
        logger.error(
            "Outbox Kafka transaction returned no result, so whether it committed is unknown; " +
                "left ${preparedEvents.size} events RETRYING for stale recovery: ids=${preparedEvents.map { it.first.id }}"
        )
        return SendAttempt.Unconfirmed
    }

    private fun markPublished(results: List<Pair<OutboxEvent, SendResult<String, Any>>>, claimedAt: LocalDateTime?) {
        brokerBackoff.recordSuccess()
        transactionTemplate.executeWithoutResult {
            if (claimedAt == null) {
                results.forEach { (event, result) -> handlePublishSuccess(event, result) }
            } else {
                markClaimedPublished(results, claimedAt)
            }
        }
    }

    private fun handleSendFailure(
        preparedEvents: List<Pair<OutboxEvent, Any>>,
        attempt: SendAttempt.Failed,
        claimedAt: LocalDateTime?,
        claimKind: String,
        roundStartedAt: LocalDateTime
    ): PublishOutcome {
        val events = preparedEvents.map { it.first }
        if (claimExpired(claimedAt)) {
            logger.warn(
                "Outbox $claimKind claim expired before failure handling (limit ${maxClaimAgeMs}ms); " +
                    "released ${events.size} events to PENDING without using a retry: ids=${events.map { it.id }}"
            )
            if (attempt.side == OutboxFailureClassifier.Side.MESSAGE) {
                releaseClaims(events, claimedAt)
            } else {
                releaseAfterBrokerFailure(events, attempt.failure, claimedAt)
            }
            return PublishOutcome.LEFT_CLAIMED
        }
        if (attempt.side == OutboxFailureClassifier.Side.BROKER) {
            return releaseAfterBrokerFailure(events, attempt.failure, claimedAt)
        }
        if (events.size == 1) {
            transactionTemplate.executeWithoutResult {
                recordFailure(events.single(), attempt.failure, claimedAt)
            }
            return PublishOutcome.MESSAGE_FAILED
        }
        logger.warn(
            "Outbox batch of ${events.size} events was rejected for a message-side reason; " +
                "publishing them one by one so only the rejected events use a retry"
        )
        return publishOneByOne(preparedEvents, claimedAt, claimKind, roundStartedAt)
    }

    private fun releaseAfterBrokerFailure(
        events: List<OutboxEvent>,
        failure: Exception,
        claimedAt: LocalDateTime?
    ): PublishOutcome {
        try {
            releaseClaims(events, claimedAt)
        } finally {
            backOffAfterBrokerFailure(failure, events)
        }
        return PublishOutcome.BROKER_FAILED
    }

    private fun publishOneByOne(
        preparedEvents: List<Pair<OutboxEvent, Any>>,
        claimedAt: LocalDateTime?,
        claimKind: String,
        roundStartedAt: LocalDateTime
    ): PublishOutcome {
        var outcome = PublishOutcome.PUBLISHED
        val failedKeys = mutableSetOf<String>()
        val heldBack = mutableListOf<OutboxEvent>()
        preparedEvents.forEachIndexed { index, prepared ->
            if (outOfTime(claimedAt, roundStartedAt)) {
                val remaining = heldBack + preparedEvents.drop(index).map { it.first }
                logger.warn(
                    "Outbox one-by-one publishing ran out of time; released ${remaining.size} events to PENDING " +
                        "without using a retry: ids=${remaining.map { it.id }}"
                )
                releaseClaims(remaining, claimedAt)
                return PublishOutcome.LEFT_CLAIMED
            }
            val key = orderingKey(prepared.first)
            if (key in failedKeys) {
                heldBack.add(prepared.first)
                return@forEachIndexed
            }
            when (publishPrepared(listOf(prepared), claimedAt, claimKind, roundStartedAt)) {
                PublishOutcome.PUBLISHED -> Unit
                PublishOutcome.MESSAGE_FAILED -> {
                    outcome = PublishOutcome.MESSAGE_FAILED
                    failedKeys.add(key)
                }
                PublishOutcome.BROKER_FAILED -> {
                    releaseClaims(heldBack + preparedEvents.drop(index + 1).map { it.first }, claimedAt)
                    return PublishOutcome.BROKER_FAILED
                }
                PublishOutcome.LEFT_CLAIMED -> {
                    releaseClaims(heldBack + preparedEvents.drop(index + 1).map { it.first }, claimedAt)
                    return PublishOutcome.LEFT_CLAIMED
                }
            }
        }
        releaseHeldBack(heldBack, claimedAt)
        return outcome
    }

    private fun publishIsolated(
        preparedEvents: List<Pair<OutboxEvent, Any>>,
        claimedAt: LocalDateTime?,
        roundStartedAt: LocalDateTime
    ): PublishOutcome {
        if (preparedEvents.isEmpty()) return PublishOutcome.PUBLISHED
        val head = preparedEvents.first()
        val headEvent = head.first
        val (sameKeyAsHead, others) = preparedEvents.drop(1).partition { orderingKey(it.first) == orderingKey(headEvent) }
        val allEvents = preparedEvents.map { it.first }
        logger.warn(
            "Outbox broker-side failures repeated; sending the oldest event ${headEvent.id} alone " +
                "before the other ${preparedEvents.size - 1} events"
        )
        val headFailure = when (val headAttempt = send(listOf(head), claimedAt, BATCH_CLAIM)) {
            is SendAttempt.Sent -> {
                markPublished(headAttempt.results, claimedAt)
                brokerBackoff.forgetIsolatedHeadFailure()
                return publishPrepared(preparedEvents.drop(1), claimedAt, BATCH_CLAIM, roundStartedAt)
            }
            SendAttempt.Unsent -> {
                releaseClaims(allEvents, claimedAt)
                return PublishOutcome.LEFT_CLAIMED
            }
            SendAttempt.Unconfirmed -> {
                releaseClaims(allEvents.drop(1), claimedAt)
                return PublishOutcome.LEFT_CLAIMED
            }
            is SendAttempt.Failed -> headAttempt
        }
        if (headFailure.side == OutboxFailureClassifier.Side.MESSAGE) {
            brokerBackoff.forgetIsolatedHeadFailure()
            val headOutcome = handleSendFailure(listOf(head), headFailure, claimedAt, BATCH_CLAIM, roundStartedAt)
            if (headOutcome == PublishOutcome.LEFT_CLAIMED || outOfTime(claimedAt, roundStartedAt)) {
                releaseClaims(allEvents.drop(1), claimedAt)
                return PublishOutcome.LEFT_CLAIMED
            }
            releaseHeldBack(sameKeyAsHead.map { it.first }, claimedAt)
            publishPrepared(others, claimedAt, BATCH_CLAIM, roundStartedAt)
            return PublishOutcome.MESSAGE_FAILED
        }
        if (others.isEmpty() || outOfTime(claimedAt, roundStartedAt)) {
            brokerBackoff.forgetIsolatedHeadFailure()
            return releaseAfterBrokerFailure(allEvents, headFailure.failure, claimedAt)
        }
        return when (val restAttempt = send(others, claimedAt, BATCH_CLAIM)) {
            is SendAttempt.Sent -> {
                markPublished(restAttempt.results, claimedAt)
                handleSuspectHead(headEvent, headFailure.failure, claimedAt)
                releaseHeldBack(sameKeyAsHead.map { it.first }, claimedAt)
                PublishOutcome.MESSAGE_FAILED
            }
            is SendAttempt.Failed -> {
                if (restAttempt.side == OutboxFailureClassifier.Side.BROKER) {
                    brokerBackoff.forgetIsolatedHeadFailure()
                    releaseAfterBrokerFailure(allEvents, restAttempt.failure, claimedAt)
                } else {
                    releaseClaims(listOf(headEvent) + sameKeyAsHead.map { it.first }, claimedAt)
                    handleSendFailure(others, restAttempt, claimedAt, BATCH_CLAIM, roundStartedAt)
                }
            }
            SendAttempt.Unsent -> {
                releaseClaims(allEvents, claimedAt)
                PublishOutcome.LEFT_CLAIMED
            }
            SendAttempt.Unconfirmed -> {
                releaseClaims(listOf(headEvent) + sameKeyAsHead.map { it.first }, claimedAt)
                PublishOutcome.LEFT_CLAIMED
            }
        }
    }

    private fun handleSuspectHead(headEvent: OutboxEvent, failure: Exception, claimedAt: LocalDateTime?) {
        if (brokerBackoff.recordIsolatedHeadFailure(headEvent.id)) {
            logger.warn(
                "Outbox event ${headEvent.id} failed alone in two isolated rounds in a row while the rest published; " +
                    "charging it a retry"
            )
            transactionTemplate.executeWithoutResult { recordFailure(headEvent, failure, claimedAt) }
        } else {
            logger.warn(
                "Outbox event ${headEvent.id} failed alone while the rest published; released it without using a retry " +
                    "and will charge it if it fails alone again in the next isolated round"
            )
            releaseClaims(listOf(headEvent), claimedAt)
        }
    }

    private fun releaseHeldBack(events: List<OutboxEvent>, claimedAt: LocalDateTime?) {
        if (events.isEmpty()) return
        logger.info(
            "Outbox held back ${events.size} events that share a key with a failed event to keep per-key order: " +
                "ids=${events.map { it.id }}"
        )
        releaseClaims(events, claimedAt)
    }

    private fun orderingKey(event: OutboxEvent): String =
        event.partitionKey ?: "${event.aggregateType}:${event.aggregateId}"

    private fun claimExpired(claimedAt: LocalDateTime?): Boolean =
        claimedAt != null && claimAgeMs(claimedAt) > maxClaimAgeMs

    private fun outOfTime(claimedAt: LocalDateTime?, roundStartedAt: LocalDateTime): Boolean =
        if (claimedAt != null) {
            claimExpired(claimedAt)
        } else {
            Duration.between(roundStartedAt, LocalDateTime.now(clock)).toMillis() > maxClaimAgeMs / 2
        }

    private fun releaseClaims(events: List<OutboxEvent>, claimedAt: LocalDateTime?) {
        if (events.isEmpty()) return
        transactionTemplate.executeWithoutResult {
            if (claimedAt == null) {
                events.forEach { event ->
                    event.releaseClaim()
                    outboxEventRepository.save(event)
                }
            } else {
                val ids = events.mapNotNull { it.id }
                val released = outboxEventRepository.releaseClaimedEvents(
                    ids = ids,
                    pendingStatus = OutboxStatus.PENDING,
                    updatedAt = LocalDateTime.now(),
                    claimedStatus = OutboxStatus.RETRYING,
                    claimedAt = claimedAt
                )
                if (released < ids.size) {
                    logger.warn(
                        "Outbox released $released of ${ids.size} claimed events to PENDING; " +
                            "the rest were re-claimed after claim at $claimedAt: ids=$ids"
                    )
                }
            }
        }
    }

    private fun backOffAfterBrokerFailure(failure: Exception, events: List<OutboxEvent>) {
        val pause = brokerBackoff.recordFailure()
        logger.warn(
            "Outbox publishing paused for ${pause.delayMs}ms after a broker-side failure; " +
                "events go back to PENDING without using a retry: ${failure.message}"
        )
        if (pause.longBackoffAlert) {
            logger.error(
                "Outbox publishing has been backing off for ${pause.streakMs}ms after " +
                    "${pause.consecutiveFailures} consecutive broker-side failures; " +
                    "first claimed event id=${events.firstOrNull()?.id}: ${failure.message}"
            )
        }
    }

    fun isBrokerBackingOff(): Boolean = brokerBackoff.isBackingOff()

    fun staleClaimTimestamp(): LocalDateTime? = if (batchClaim) claimTimestamp() else null

    fun publishEvent(eventId: Long, claimedAt: LocalDateTime? = null) {
        val event = transactionTemplate.execute {
            outboxEventRepository.findByIdOrNull(eventId)
        } ?: return

        if (event.processed || event.status != OutboxStatus.RETRYING) {
            return
        }

        publishPrepared(prepareEvents(listOf(event), claimedAt), claimedAt, STALE_CLAIM, LocalDateTime.now(clock))
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
            logger.error(
                "Outbox event parked after max retries for a message-side failure and excluded from publishing; " +
                    "requeue it manually once fixed: eventId=${event.id}, error=$errorMessage"
            )
        } else {
            event.markAsFailed(errorMessage)
            logger.warn("Outbox event failed, will retry: eventId=${event.id}, " +
                "retryCount=${event.retryCount}, error=$errorMessage")
        }
        outboxMetrics.recordOutboxFailed(event.topic)
    }

    private fun claimTimestamp(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)

    private fun ensureClaimFresh(claimedAt: LocalDateTime?) {
        if (claimedAt == null) return
        val ageMs = claimAgeMs(claimedAt)
        if (ageMs > maxClaimAgeMs) throw ClaimExpiredException(ageMs)
    }

    private fun claimAgeMs(claimedAt: LocalDateTime): Long =
        Duration.between(claimedAt, LocalDateTime.now(clock)).toMillis()

    private class ClaimExpiredException(val ageMs: Long) : RuntimeException("Outbox claim expired after ${ageMs}ms")

    private data class ClaimedBatch(val ids: List<Long>, val claimedAt: LocalDateTime)

    private data class BatchOutcome(val locked: Int, val halted: Boolean)

    private enum class PublishOutcome { PUBLISHED, MESSAGE_FAILED, BROKER_FAILED, LEFT_CLAIMED }

    private sealed interface SendAttempt {
        class Sent(val results: List<Pair<OutboxEvent, SendResult<String, Any>>>) : SendAttempt
        class Failed(val failure: Exception, val side: OutboxFailureClassifier.Side) : SendAttempt
        object Unsent : SendAttempt
        object Unconfirmed : SendAttempt
    }

    companion object {
        private const val BATCH_LIMIT = 100
        private const val MYSQL_PRODUCT_NAME = "MySQL"
        private const val BATCH_CLAIM = "batch"
        private const val STALE_CLAIM = "stale"
    }
}
