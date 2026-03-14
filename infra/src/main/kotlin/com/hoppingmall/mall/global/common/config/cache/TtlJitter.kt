package com.hoppingmall.mall.global.common.config.cache

import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

object TtlJitter {

    fun apply(baseTtl: Duration, jitterPercent: Int): Duration {
        if (jitterPercent <= 0) return baseTtl
        val baseSeconds = baseTtl.seconds
        val maxJitter = baseSeconds * jitterPercent / 100
        val jitter = ThreadLocalRandom.current().nextLong(0, maxJitter + 1)
        return Duration.ofSeconds(baseSeconds + jitter)
    }
}
