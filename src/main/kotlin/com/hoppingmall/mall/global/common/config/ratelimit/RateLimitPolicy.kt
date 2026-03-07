package com.hoppingmall.mall.global.common.config.ratelimit

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
            RateLimitPolicy("/api/v1/auth/refresh", "POST", RateLimitKeyType.IP, 5, 5, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/coupons/*/issue", "POST", RateLimitKeyType.USER_ID, 5, 5, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/products/search", "GET", RateLimitKeyType.IP, 20, 20, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/products/images/upload", "POST", RateLimitKeyType.USER_ID, 20, 20, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, 5, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/refunds", "POST", RateLimitKeyType.USER_ID, 5, 5, Duration.ofMinutes(1)),
            RateLimitPolicy("/api/v1/reviews", "POST", RateLimitKeyType.USER_ID, 5, 5, Duration.ofMinutes(1))
        )
    }
}
