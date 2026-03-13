package com.hoppingmall.mall.global.common.config.cache

import java.io.Closeable
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

open class HotKeyDetector(
    private val threshold: Long,
    windowDuration: Duration
) : Closeable {

    private val counts = ConcurrentHashMap<String, LongAdder>()
    private val hotKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "hotkey-detector").apply { isDaemon = true }
    }

    init {
        val windowMs = windowDuration.toMillis()
        scheduler.scheduleAtFixedRate(::reset, windowMs, windowMs, TimeUnit.MILLISECONDS)
    }

    open fun recordAccess(key: String) {
        val adder = counts.computeIfAbsent(key) { LongAdder() }
        adder.increment()
        if (adder.sum() >= threshold) {
            hotKeys.add(key)
        }
    }

    open fun isHot(key: String): Boolean = hotKeys.contains(key)

    private fun reset() {
        counts.clear()
        hotKeys.clear()
    }

    override fun close() {
        scheduler.shutdown()
    }
}
