package com.hoppingmall.cache

import org.redisson.api.RedissonClient
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class HotKeyDetectorRegistry(
    policies: Collection<CachePolicy>,
    private val detectorType: String = "local",
    private val redissonClient: RedissonClient? = null
) : Closeable {

    private val scheduler: ScheduledExecutorService = run {
        val activePolicies = policies.count { it.dynamicHotKeyEnabled }
        val threadCount = minOf(activePolicies, 4).coerceAtLeast(1)
        Executors.newScheduledThreadPool(threadCount) { r ->
            Thread(r, "hotkey-detector").apply { isDaemon = true }
        }
    }

    private val detectors: Map<String, HotKeyDetector> =
        policies.filter { it.dynamicHotKeyEnabled }
            .associate { policy ->
                policy.cacheName to createDetector(policy)
            }

    private fun createDetector(policy: CachePolicy): HotKeyDetector {
        if (detectorType == "redis" && redissonClient != null) {
            return RedisHotKeyDetector(
                cacheName = policy.cacheName,
                threshold = policy.hotKeyThreshold,
                windowMs = policy.hotKeyWindow.toMillis(),
                redissonClient = redissonClient,
                scheduler = scheduler
            )
        }
        return LocalHotKeyDetector(policy.hotKeyThreshold, policy.hotKeyWindow, scheduler)
    }

    fun getDetector(cacheName: String): HotKeyDetector? = detectors[cacheName]

    override fun close() {
        detectors.values.forEach { it.close() }
        scheduler.shutdown()
    }
}
