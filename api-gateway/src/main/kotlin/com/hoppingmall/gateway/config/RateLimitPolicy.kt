package com.hoppingmall.gateway.config

import java.time.Duration

enum class RateLimitKeyType {
    IP, USER_ID, REFRESH_TOKEN
}

data class RateLimitPolicy(
    val id: String,
    val pathPattern: String,
    val httpMethod: String? = null,
    val keyType: RateLimitKeyType,
    val maxRequests: Long,
    val window: Duration
)
