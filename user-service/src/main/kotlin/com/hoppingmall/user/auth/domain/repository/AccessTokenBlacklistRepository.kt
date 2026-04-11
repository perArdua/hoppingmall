package com.hoppingmall.user.auth.domain.repository

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class AccessTokenBlacklistRepository(
    @Qualifier("stringRedisTemplate")
    private val stringRedisTemplate: RedisTemplate<String, String>
) {
    private val prefix = "blacklist:"

    fun add(token: String, remainingTtlMs: Long) {
        if (remainingTtlMs <= 0) return
        stringRedisTemplate.opsForValue()
            .set(getKey(token), "blacklisted", remainingTtlMs, TimeUnit.MILLISECONDS)
    }

    fun exists(token: String): Boolean {
        return stringRedisTemplate.hasKey(getKey(token))
    }

    private fun getKey(token: String) = "$prefix$token"
}
