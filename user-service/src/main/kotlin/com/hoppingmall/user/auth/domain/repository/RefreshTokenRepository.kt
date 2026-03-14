package com.hoppingmall.user.auth.domain.repository

import com.hoppingmall.user.auth.domain.RefreshToken
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class RefreshTokenRepository(
    @Qualifier("stringRedisTemplate")
    private val stringRedisTemplate: RedisTemplate<String, String>
) {
    private val prefix = "refreshToken:"

    fun save(refreshToken: RefreshToken) {
        val key = getKey(refreshToken.userId)
        stringRedisTemplate.opsForValue()
            .set(key, refreshToken.token, refreshToken.ttl, TimeUnit.MILLISECONDS)
    }

    fun findByUserId(userId: Long): String? {
        return stringRedisTemplate.opsForValue().get(getKey(userId))
    }

    fun deleteByUserId(userId: Long) {
        stringRedisTemplate.delete(getKey(userId))
    }

    private fun getKey(userId: Long) = "$prefix$userId"
}
