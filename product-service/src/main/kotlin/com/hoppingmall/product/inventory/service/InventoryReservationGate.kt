package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.exception.InventoryBusyException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class InventoryReservationGate(
    meterRegistry: MeterRegistry,
    @Value("\${inventory.bucket.count:8}") bucketCount: Int,
    @Value("\${inventory.semaphore.permits:0}") permitsOverride: Int
) {
    val permits: Int = if (permitsOverride > 0) permitsOverride else bucketCount

    private val semaphores = ConcurrentHashMap<Long, Semaphore>()

    private val inFlightKeys = ConcurrentHashMap<String, InFlightKey>()

    private val queueFullCounter = Counter.builder("inventory.reserve.rejected")
        .tag("reason", "queue_full")
        .register(meterRegistry)

    init {
        Gauge.builder("inventory.semaphore.available", this) { gate -> gate.minAvailable().toDouble() }
            .description("Smallest number of free reserve permits across products")
            .register(meterRegistry)
    }

    fun <T> withPermits(productIds: Collection<Long>, block: () -> T): T {
        val acquired = acquire(productIds)
        try {
            return block()
        } finally {
            acquired.forEach { it.release() }
        }
    }

    fun <T> withPermits(productIds: Collection<Long>, requestKey: String, block: () -> T): T {
        enter(requestKey)
        try {
            return withPermits(productIds, block)
        } finally {
            leave(requestKey)
        }
    }

    fun awaitInFlight(requestKey: String, timeoutMs: Long): Boolean {
        val inFlight = inFlightKeys[requestKey] ?: return true
        if (timeoutMs <= 0) return false
        return try {
            inFlight.done.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (e: TimeoutException) {
            false
        }
    }

    fun minAvailable(): Int = semaphores.values.minOfOrNull { it.availablePermits() } ?: permits

    private fun enter(requestKey: String) {
        inFlightKeys.compute(requestKey) { _, current -> (current ?: InFlightKey()).also { it.holders++ } }
    }

    private fun leave(requestKey: String) {
        inFlightKeys.computeIfPresent(requestKey) { _, current ->
            current.holders--
            if (current.holders > 0) {
                current
            } else {
                current.done.complete(Unit)
                null
            }
        }
    }

    private class InFlightKey {
        var holders = 0
        val done = CompletableFuture<Unit>()
    }

    private fun acquire(productIds: Collection<Long>): List<Semaphore> {
        val acquired = mutableListOf<Semaphore>()
        for (productId in productIds.distinct().sorted()) {
            val semaphore = semaphores.computeIfAbsent(productId) { Semaphore(permits) }
            if (!semaphore.tryAcquire()) {
                acquired.forEach { it.release() }
                queueFullCounter.increment()
                throw InventoryBusyException()
            }
            acquired.add(semaphore)
        }
        return acquired
    }
}
