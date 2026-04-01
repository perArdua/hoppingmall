package com.hoppingmall.cache

import java.time.Duration

data class CachePolicy(
    val cacheName: String,
    val l1MaxSize: Long,
    val l1Ttl: Duration,
    val l2Ttl: Duration,
    val jitterPercent: Int = 10,
    val hotKeyThreshold: Long = 0L,
    val hotKeyWindow: Duration = Duration.ofSeconds(60),
    val hotKeyShardCount: Int = 4
) {
    val dynamicHotKeyEnabled: Boolean get() = hotKeyThreshold > 0L
}
