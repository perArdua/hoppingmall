package com.hoppingmall.mall.global.auth.domain.repository

import com.hoppingmall.mall.global.auth.domain.RefreshToken
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class RefreshTokenRepository(
    private val redisTemplate: StringRedisTemplate
) {
    private val prefix = "refreshToken:"

    fun save(refreshToken: RefreshToken) {
        val key = getKey(refreshToken.userId)
        redisTemplate.opsForValue()
            .set(key, refreshToken.token, refreshToken.ttl, TimeUnit.MILLISECONDS)
    }

    fun findByUserId(userId: Long): String? {
        return redisTemplate.opsForValue().get(getKey(userId))
    }

    fun deleteByUserId(userId: Long) {
        redisTemplate.delete(getKey(userId))
    }

    private fun getKey(userId: Long) = "$prefix$userId"
}