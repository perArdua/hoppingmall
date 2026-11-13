package com.hoppingmall.user.config.ratelimit

import java.time.Duration

class NoOpLoginAttemptLimiter : LoginAttemptLimiter {
    override fun remainingBlock(email: String): Duration = Duration.ZERO
    override fun recordFailure(email: String) {}
    override fun reset(email: String) {}
}
