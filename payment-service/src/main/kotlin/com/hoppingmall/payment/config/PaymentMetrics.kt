package com.hoppingmall.payment.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PaymentMetrics(private val registry: MeterRegistry) {

    private val completedCounter = Counter.builder("payment.completed.count")
        .description("Total number of successful payments")
        .register(registry)

    private val failedCounter = Counter.builder("payment.failed.count")
        .description("Total number of failed payments")
        .register(registry)

    private val cancelledCounter = Counter.builder("payment.cancelled.count")
        .description("Total number of cancelled payments")
        .register(registry)

    private val amountCounter = Counter.builder("payment.amount.total")
        .description("Total payment amount processed")
        .register(registry)

    private val processingTimer = Timer.builder("payment.processing.duration")
        .description("Payment processing duration")
        .register(registry)

    fun recordPaymentCompleted(amount: BigDecimal, method: String) {
        completedCounter.increment()
        amountCounter.increment(amount.toDouble())
        Counter.builder("payment.completed.count.by_method")
            .tag("method", method)
            .register(registry)
            .increment()
    }

    fun recordPaymentFailed() {
        failedCounter.increment()
    }

    fun recordPaymentCancelled() {
        cancelledCounter.increment()
    }

    fun <T> recordProcessingTime(block: () -> T): T {
        return processingTimer.recordCallable(block)!!
    }
}
