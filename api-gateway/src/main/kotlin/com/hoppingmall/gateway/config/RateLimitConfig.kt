package com.hoppingmall.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class RateLimitConfig {

    @Bean
    fun rateLimitPolicies(): List<RateLimitPolicy> = listOf(
        RateLimitPolicy("login", "/api/v1/users/login", "POST", RateLimitKeyType.IP, 5, Duration.ofMinutes(1)),
        RateLimitPolicy("signup", "/api/v1/users/signup", "POST", RateLimitKeyType.IP, 3, Duration.ofMinutes(1)),
        RateLimitPolicy("refresh", "/api/v1/auth/refresh", "POST", RateLimitKeyType.USER_ID, 10, Duration.ofMinutes(1)),
        RateLimitPolicy("coupon-issue", "/api/v1/coupons/*/issue", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1)),
        RateLimitPolicy("search", "/api/v1/products/search", "GET", RateLimitKeyType.USER_ID, 60, Duration.ofMinutes(1)),
        RateLimitPolicy("upload", "/api/v1/products/images/upload", "POST", RateLimitKeyType.USER_ID, 20, Duration.ofMinutes(1)),
        RateLimitPolicy("create-order", "/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1)),
        RateLimitPolicy("create-payment", "/api/v1/payments", "POST", RateLimitKeyType.USER_ID, 10, Duration.ofMinutes(1)),
        RateLimitPolicy("create-refund", "/api/v1/refunds", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1)),
        RateLimitPolicy("create-review", "/api/v1/reviews", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1))
    )
}
