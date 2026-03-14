package com.hoppingmall.user.config.ratelimit

import java.time.Duration

enum class RateLimitKeyType { IP, USER_ID }

data class RateLimitPolicy(
    val pathPattern: String,
    val httpMethod: String,
    val keyType: RateLimitKeyType,
    val capacity: Long,
    val refillTokens: Long,
    val refillDuration: Duration
) {
    companion object {
        val POLICIES = listOf(
            RateLimitPolicy("/api/v1/users/login", "POST", RateLimitKeyType.IP, 5, 5, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/users/signup", "POST", RateLimitKeyType.IP, 3, 3, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/auth/refresh", "POST", RateLimitKeyType.IP, 5, 5, Duration.ofMinutes(1))
        )
    }
}
