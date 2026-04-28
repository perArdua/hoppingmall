package com.hoppingmall.cache

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class LocalHotKeyDetector(
    private val threshold: Long,
    windowDuration: Duration,
    scheduler: ScheduledExecutorService
) : HotKeyDetector {

    private val counts = ConcurrentHashMap<String, LongAdder>()
    private val hotKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        val windowMs = windowDuration.toMillis()
        scheduler.scheduleWithFixedDelay(::reset, windowMs, windowMs, TimeUnit.MILLISECONDS)
    }

    override fun recordAccess(key: String) {
        val adder = counts.computeIfAbsent(key) { LongAdder() }
        adder.increment()
        if (adder.sum() >= threshold) {
            hotKeys.add(key)
        }
    }

    override fun isHot(key: String): Boolean = hotKeys.contains(key)

    private fun reset() {
        counts.clear()
        hotKeys.clear()
    }

    override fun close() {
    }
}
