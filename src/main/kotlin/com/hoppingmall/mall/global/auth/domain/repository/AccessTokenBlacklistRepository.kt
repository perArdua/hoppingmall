package com.hoppingmall.mall.global.auth.domain.repository

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class AccessTokenBlacklistRepository(
    private val customStringRedisTemplate: RedisTemplate<String, String>
) {
    private val prefix = "blacklist:"

    fun add(token: String, remainingTtlMs: Long) {
        if (remainingTtlMs <= 0) return
        customStringRedisTemplate.opsForValue()
            .set(getKey(token), "blacklisted", remainingTtlMs, TimeUnit.MILLISECONDS)
    }

    fun exists(token: String): Boolean {
        return customStringRedisTemplate.hasKey(getKey(token))
    }

    private fun getKey(token: String) = "$prefix$token"
}
