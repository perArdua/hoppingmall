package com.hoppingmall.gateway.config

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

@Configuration
class RateLimitConfig {

    @Bean
    fun userKeyResolver(): KeyResolver {
        return KeyResolver { exchange ->
            val userId = exchange.request.headers.getFirst("x-user-id")
            if (userId != null) {
                Mono.just("user:$userId")
            } else {
                Mono.just("ip:${exchange.request.remoteAddress?.address?.hostAddress ?: "anonymous"}")
            }
        }
    }

    @Bean
    fun redisRateLimiter(): RedisRateLimiter {
        return RedisRateLimiter(50, 100, 1)
    }
}
