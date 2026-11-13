package com.hoppingmall.user.config.ratelimit

import java.time.Duration

interface LoginAttemptLimiter {
    fun remainingBlock(email: String): Duration
    fun recordFailure(email: String)
    fun reset(email: String)
}
