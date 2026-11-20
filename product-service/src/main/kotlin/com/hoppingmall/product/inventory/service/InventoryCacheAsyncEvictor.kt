package com.hoppingmall.product.inventory.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["inventory.cache.evict-async"], havingValue = "true", matchIfMissing = true)
class InventoryCacheAsyncEvictor(
    private val cacheManagerProvider: ObjectProvider<CacheManager>,
    meterRegistry: MeterRegistry,
    @Value("\${inventory.cache.evict-async-queue-capacity:1024}") queueCapacity: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val pending: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(queueCapacity),
        { runnable -> Thread(runnable, "inventory-cache-evict").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    private val evictedCounter = counter(meterRegistry, "evicted")
    private val mergedCounter = counter(meterRegistry, "merged")
    private val droppedCounter = counter(meterRegistry, "dropped")
    private val failedCounter = counter(meterRegistry, "failed")

    init {
        Gauge.builder("inventory.cache.evict.async.queue", executor) { it.queue.size.toDouble() }
            .register(meterRegistry)
    }

    fun submit(productIds: Collection<Long>) {
        productIds.forEach { productId ->
            if (!pending.add(productId)) {
                mergedCounter.increment()
                return@forEach
            }
            try {
                executor.execute { evict(productId) }
            } catch (e: RejectedExecutionException) {
                pending.remove(productId)
                droppedCounter.increment()
            }
        }
    }

    fun queueSize(): Int = executor.queue.size

    private fun evict(productId: Long) {
        pending.remove(productId)
        try {
            cacheManagerProvider.getIfAvailable()?.getCache(INVENTORY_CACHE)?.evict(productId)
            evictedCounter.increment()
        } catch (e: Exception) {
            failedCounter.increment()
            log.warn("재고 캐시 비동기 삭제 실패: productId={}, cause={}", productId, e.toString())
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
    }

    private fun counter(meterRegistry: MeterRegistry, result: String): Counter =
        Counter.builder("inventory.cache.evict.async").tag("result", result).register(meterRegistry)

    companion object {
        private const val INVENTORY_CACHE = "inventory"
    }
}
