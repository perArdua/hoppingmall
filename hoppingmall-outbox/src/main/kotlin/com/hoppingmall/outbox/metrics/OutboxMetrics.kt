package com.hoppingmall.outbox.metrics

import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class OutboxMetrics(
    private val registry: MeterRegistry,
    private val outboxEventRepository: OutboxEventRepository
) {

    private val publishedCounter = Counter.builder("outbox.event.published.count")
        .description("Total number of outbox events published")
        .register(registry)

    private val failedCounter = Counter.builder("outbox.event.failed.count")
        .description("Total number of outbox event publish failures")
        .register(registry)

    private val topicPublishedCounters = ConcurrentHashMap<String, Counter>()
    private val topicFailedCounters = ConcurrentHashMap<String, Counter>()

    private val publishLatency = Timer.builder("outbox.event.publish.latency")
        .description("Time from event creation to successful publish")
        .register(registry)

    init {
        Gauge.builder("outbox.event.pending.count") {
            outboxEventRepository.countByStatus(OutboxStatus.PENDING).toDouble()
        }.description("Pending outbox events").register(registry)

        Gauge.builder("outbox.event.retrying.count") {
            outboxEventRepository.countByStatus(OutboxStatus.RETRYING).toDouble()
        }.description("Retrying outbox events").register(registry)

        Gauge.builder("outbox.event.failed.current") {
            outboxEventRepository.countByStatus(OutboxStatus.FAILED).toDouble()
        }.description("Failed outbox events").register(registry)
    }

    fun recordOutboxPublished(topic: String) {
        publishedCounter.increment()
        topicPublishedCounters.computeIfAbsent(topic) {
            Counter.builder("outbox.event.published.by_topic").tag("topic", it).register(registry)
        }.increment()
    }

    fun recordOutboxFailed(topic: String) {
        failedCounter.increment()
        topicFailedCounters.computeIfAbsent(topic) {
            Counter.builder("outbox.event.failed.by_topic").tag("topic", it).register(registry)
        }.increment()
    }

    fun recordPublishLatency(createdAt: LocalDateTime) {
        val latencyMs = Duration.between(createdAt, LocalDateTime.now()).toMillis()
        publishLatency.record(Duration.ofMillis(latencyMs))
    }
}
