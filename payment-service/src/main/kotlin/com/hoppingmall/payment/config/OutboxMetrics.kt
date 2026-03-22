package com.hoppingmall.payment.config

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

    private val dlqSavedCounter = Counter.builder("dlq.message.saved.count")
        .description("Total number of messages saved to DLQ")
        .register(registry)

    private val dlqRetrySuccessCounter = Counter.builder("dlq.retry.success.count")
        .description("Total number of successful DLQ retries")
        .register(registry)

    private val dlqRetryFailedCounter = Counter.builder("dlq.retry.failed.count")
        .description("Total number of failed DLQ retries")
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

    fun recordDlqSaved(topic: String) {
        dlqSavedCounter.increment()
        Counter.builder("dlq.message.saved.by_topic")
            .tag("topic", topic)
            .register(registry)
            .increment()
    }

    fun recordDlqRetrySuccess() {
        dlqRetrySuccessCounter.increment()
    }

    fun recordDlqRetryFailed() {
        dlqRetryFailedCounter.increment()
    }
}
