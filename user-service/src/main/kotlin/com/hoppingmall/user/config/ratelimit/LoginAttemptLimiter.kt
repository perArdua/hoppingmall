package com.hoppingmall.user.config.ratelimit

import java.time.Duration

interface LoginAttemptLimiter {
    fun remainingBlock(email: String, clientIp: String): Duration
    fun recordFailure(email: String, clientIp: String)
    fun reset(email: String, clientIp: String)
}
