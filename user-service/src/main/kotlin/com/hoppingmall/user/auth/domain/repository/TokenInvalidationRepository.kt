package com.hoppingmall.user.auth.domain.repository

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class TokenInvalidationRepository(
    @Qualifier("stringRedisTemplate")
    private val stringRedisTemplate: RedisTemplate<String, String>
) {

    fun markInvalidBefore(userId: Long, invalidBeforeEpochMs: Long, ttlMs: Long) {
        stringRedisTemplate.opsForValue()
            .set(KEY_PREFIX + userId, invalidBeforeEpochMs.toString(), ttlMs, TimeUnit.MILLISECONDS)
    }

    companion object {
        const val KEY_PREFIX = "token-invalid-before:"
    }
}
