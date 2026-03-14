package com.hoppingmall.gateway.config

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
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
                Mono.just(userId)
            } else {
                Mono.just(exchange.request.remoteAddress?.address?.hostAddress ?: "anonymous")
            }
        }
    }
}
