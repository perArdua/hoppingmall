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

    init {
        outboxMetrics.bindBrokerBackoff { isBackingOff() }
    }

    @Synchronized
    fun isBackingOff(): Boolean = clock.millis() < backoffUntilMillis

    @Synchronized
    fun recordFailure(): Long {
        consecutiveFailures++
        val delayMs = jittered(baseDelayMs())
        backoffUntilMillis = clock.millis() + delayMs
        outboxMetrics.recordBrokerFailure()
        return delayMs
    }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
        backoffUntilMillis = 0L
    }

    private fun baseDelayMs(): Long {
        val doublings = (consecutiveFailures - 1).coerceAtMost(MAX_DOUBLINGS)
        return (initialMs shl doublings).coerceAtMost(maxMs)
    }

    private fun jittered(delayMs: Long): Long =
        delayMs - (delayMs * JITTER_RATIO * random.nextDouble()).toLong()

    companion object {
        private const val JITTER_RATIO = 0.2
        private const val MAX_DOUBLINGS = 30
    }
}
