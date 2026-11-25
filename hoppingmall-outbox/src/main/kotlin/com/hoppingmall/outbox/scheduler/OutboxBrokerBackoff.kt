package com.hoppingmall.outbox.scheduler

import com.hoppingmall.outbox.metrics.OutboxMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.random.Random

@Component
class OutboxBrokerBackoff(
    private val outboxMetrics: OutboxMetrics,
    @Value("\${outbox.publish.broker-backoff.initial-ms:1000}")
    private val initialMs: Long = 1_000,
    @Value("\${outbox.publish.broker-backoff.max-ms:30000}")
    private val maxMs: Long = 30_000,
    private val clock: Clock = Clock.systemUTC(),
    private val random: Random = Random.Default
) {

    private var consecutiveFailures = 0
    private var backoffUntilMillis = 0L
    private var streakStartedAtMillis = 0L
    private var lastLongBackoffAlertAtMillis: Long? = null

    init {
        outboxMetrics.bindBrokerBackoff { isBackingOff() }
    }

    @Synchronized
    fun isBackingOff(): Boolean = clock.millis() < backoffUntilMillis

    @Synchronized
    fun shouldIsolateEvents(): Boolean = consecutiveFailures >= ISOLATION_STREAK

    @Synchronized
    fun recordFailure(): Pause {
        val now = clock.millis()
        if (consecutiveFailures == 0) streakStartedAtMillis = now
        consecutiveFailures++
        val delayMs = jittered(baseDelayMs())
        backoffUntilMillis = now + delayMs
        outboxMetrics.recordBrokerFailure()
        val streakMs = now - streakStartedAtMillis
        val lastAlert = lastLongBackoffAlertAtMillis
        val alert = streakMs > LONG_BACKOFF_ALERT_MS && (lastAlert == null || now - lastAlert >= LONG_BACKOFF_ALERT_MS)
        if (alert) lastLongBackoffAlertAtMillis = now
        return Pause(delayMs, consecutiveFailures, streakMs, alert)
    }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
        backoffUntilMillis = 0L
        lastLongBackoffAlertAtMillis = null
    }

    private fun baseDelayMs(): Long {
        val doublings = (consecutiveFailures - 1).coerceAtMost(MAX_DOUBLINGS)
        return (initialMs shl doublings).coerceAtMost(maxMs)
    }

    private fun jittered(delayMs: Long): Long =
        delayMs - (delayMs * JITTER_RATIO * random.nextDouble()).toLong()

    data class Pause(
        val delayMs: Long,
        val consecutiveFailures: Int,
        val streakMs: Long,
        val longBackoffAlert: Boolean
    )

    companion object {
        private const val ISOLATION_STREAK = 3
        private const val LONG_BACKOFF_ALERT_MS = 300_000L
        private const val JITTER_RATIO = 0.2
        private const val MAX_DOUBLINGS = 30
    }
}
