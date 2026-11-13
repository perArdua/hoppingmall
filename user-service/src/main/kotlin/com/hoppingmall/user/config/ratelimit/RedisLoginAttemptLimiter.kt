package com.hoppingmall.user.config.ratelimit

import org.redisson.api.RedissonClient
import java.time.Clock

class RedisLoginAttemptLimiter(
    private val redissonClient: RedissonClient,
    private val clock: Clock
) : LoginAttemptLimiter {

    override fun isBlocked(email: String): Boolean = false

    override fun recordFailure(email: String) {}

    override fun reset(email: String) {}
}
