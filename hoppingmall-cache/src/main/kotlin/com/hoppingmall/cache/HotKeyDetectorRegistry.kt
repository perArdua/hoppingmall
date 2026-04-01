package com.hoppingmall.cache

import java.io.Closeable

class HotKeyDetectorRegistry(
    policies: Collection<CachePolicy>
) : Closeable {

    private val detectors: Map<String, HotKeyDetector> =
        policies.filter { it.dynamicHotKeyEnabled }
            .associate { policy ->
                policy.cacheName to HotKeyDetector(policy.hotKeyThreshold, policy.hotKeyWindow)
            }

    fun getDetector(cacheName: String): HotKeyDetector? = detectors[cacheName]

    override fun close() {
        detectors.values.forEach { it.close() }
    }
}
