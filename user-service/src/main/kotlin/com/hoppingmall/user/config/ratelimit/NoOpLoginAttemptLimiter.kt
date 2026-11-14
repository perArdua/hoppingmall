package com.hoppingmall.user.config.ratelimit

import java.time.Duration

class NoOpLoginAttemptLimiter : LoginAttemptLimiter {
    override fun remainingBlock(email: String, clientIp: String): Duration = Duration.ZERO
    override fun recordFailure(email: String, clientIp: String) {}
    override fun reset(email: String, clientIp: String) {}
}
