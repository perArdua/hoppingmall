package com.hoppingmall.order.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class OrderMetrics(private val registry: MeterRegistry) {

    private val createdCounter = Counter.builder("order.created.count")
        .description("Total number of created orders")
        .register(registry)

    private val cancelledCounter = Counter.builder("order.cancelled.count")
        .description("Total number of cancelled orders")
        .register(registry)

    private val amountCounter = Counter.builder("order.amount.total")
        .description("Total order amount")
        .register(registry)

    fun recordOrderCreated(amount: BigDecimal) {
        createdCounter.increment()
        amountCounter.increment(amount.toDouble())
    }

    fun recordOrderCancelled() {
        cancelledCounter.increment()
    }
}
