package com.hoppingmall.order.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class OutboxMetrics(private val registry: MeterRegistry) {

    private val publishedCounter = Counter.builder("outbox.event.published.count")
        .description("Total number of outbox events published")
        .register(registry)

    private val failedCounter = Counter.builder("outbox.event.failed.count")
        .description("Total number of outbox event publish failures")
        .register(registry)

    fun recordOutboxPublished(topic: String) {
        publishedCounter.increment()
        Counter.builder("outbox.event.published.by_topic")
            .tag("topic", topic)
            .register(registry)
            .increment()
    }

    fun recordOutboxFailed(topic: String) {
        failedCounter.increment()
        Counter.builder("outbox.event.failed.by_topic")
            .tag("topic", topic)
            .register(registry)
            .increment()
    }
}
