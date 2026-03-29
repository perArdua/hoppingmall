package com.hoppingmall.gateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class RateLimitGlobalFilter(
    private val keyResolver: KeyResolver,
    private val redisRateLimiter: RedisRateLimiter
) : GlobalFilter {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.uri.path

        if (isExcluded(path)) {
            return chain.filter(exchange)
        }

        return keyResolver.resolve(exchange).flatMap { key ->
            redisRateLimiter.isAllowed("gateway", key).flatMap { response ->
                if (response.isAllowed) {
                    response.headers.forEach { (name, value) ->
                        exchange.response.headers[name] = listOf(value)
                    }
                    chain.filter(exchange)
                } else {
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    exchange.response.headers.set("Retry-After", "1")
                    exchange.response.setComplete()
                }
            }
        }
    }

    private fun isExcluded(path: String): Boolean {
        return path.startsWith("/actuator") ||
            path.startsWith("/internal") ||
            path == "/api/v1/users/signup" ||
            path == "/api/v1/users/login" ||
            path == "/api/v1/auth/refresh"
    }
}
