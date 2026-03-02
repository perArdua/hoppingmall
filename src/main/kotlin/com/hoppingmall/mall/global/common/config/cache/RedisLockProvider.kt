package com.hoppingmall.mall.global.common.config.cache

import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class RedisLockProvider(
    private val redisTemplate: StringRedisTemplate
) : LockProvider {

    override fun tryLock(key: String, leaseTime: Duration): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent(key, "locked", leaseTime) ?: false
    }

    override fun unlock(key: String) {
        redisTemplate.delete(key)
    }
}
