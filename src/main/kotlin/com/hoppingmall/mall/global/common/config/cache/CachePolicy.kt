package com.hoppingmall.mall.global.common.config.cache

import java.time.Duration

data class CachePolicy(
    val cacheName: String,
    val l1MaxSize: Long,
    val l1Ttl: Duration,
    val l2Ttl: Duration,
    val jitterPercent: Int = 10,
    val hotKey: Boolean = false
)
