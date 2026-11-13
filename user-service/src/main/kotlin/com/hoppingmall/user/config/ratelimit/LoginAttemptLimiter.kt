package com.hoppingmall.user.config.ratelimit

interface LoginAttemptLimiter {
    fun isBlocked(email: String): Boolean
    fun recordFailure(email: String)
    fun reset(email: String)
}
