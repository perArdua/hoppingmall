package com.hoppingmall.user.config.ratelimit

class NoOpLoginAttemptLimiter : LoginAttemptLimiter {
    override fun isBlocked(email: String): Boolean = false
    override fun recordFailure(email: String) {}
    override fun reset(email: String) {}
}
