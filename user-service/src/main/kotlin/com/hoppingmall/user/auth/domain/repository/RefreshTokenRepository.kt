package com.hoppingmall.user.auth.domain.repository

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RefreshTokenRepository(
    @Qualifier("stringRedisTemplate")
    private val stringRedisTemplate: RedisTemplate<String, String>
) {
    private val prefix = "refreshToken:"

    fun findByUserId(userId: Long): String? {
        return stringRedisTemplate.opsForValue().get(getKey(userId))
    }

    fun deleteByUserId(userId: Long) {
        stringRedisTemplate.delete(getKey(userId))
    }

    private fun getKey(userId: Long) = "$prefix$userId"
}
