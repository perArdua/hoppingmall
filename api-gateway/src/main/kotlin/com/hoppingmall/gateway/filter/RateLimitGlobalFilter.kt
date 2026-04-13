package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.RateLimitKeyType
import com.hoppingmall.gateway.config.RateLimitPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class RateLimitGlobalFilter(
    private val policies: List<RateLimitPolicy>,
    private val redisTemplate: ReactiveStringRedisTemplate,
    @Value("\${gateway.rate-limit.enabled:true}")
    private val enabled: Boolean
) : GlobalFilter {

    private val pathMatcher = AntPathMatcher()

    companion object {
        private val GLOBAL_FALLBACK = RateLimitPolicy(
            id = "global",
            pathPattern = "/**",
            keyType = RateLimitKeyType.USER_ID,
            maxRequests = 200,
            window = Duration.ofMinutes(1)
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        if (!enabled) {
            return chain.filter(exchange)
        }

        val path = exchange.request.uri.path

        if (isExcluded(path)) {
            return chain.filter(exchange)
        }

        val method = exchange.request.method.name()
        val policy = findPolicy(path, method) ?: GLOBAL_FALLBACK

        return checkLimit(exchange, chain, policy)
    }

    private fun findPolicy(path: String, method: String?): RateLimitPolicy? {
        return policies.find { policy ->
            pathMatcher.match(policy.pathPattern, path) &&
                (policy.httpMethod == null || policy.httpMethod == method)
        }
    }

    private fun resolveKey(exchange: ServerWebExchange, keyType: RateLimitKeyType): String {
        return when (keyType) {
            RateLimitKeyType.USER_ID -> {
                exchange.request.headers.getFirst("x-user-id")
                    ?: exchange.request.remoteAddress?.address?.hostAddress
                    ?: "anonymous"
            }
            RateLimitKeyType.IP -> {
                exchange.request.remoteAddress?.address?.hostAddress ?: "anonymous"
            }
        }
    }

    private fun checkLimit(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
        policy: RateLimitPolicy
    ): Mono<Void> {
        val key = resolveKey(exchange, policy.keyType)
        val windowSeconds = policy.window.seconds
        val window = System.currentTimeMillis() / (windowSeconds * 1000)
        val redisKey = "rl:${policy.id}:$key:$window"

        return redisTemplate.opsForValue().increment(redisKey)
            .flatMap { count ->
                if (count == 1L) {
                    redisTemplate.expire(redisKey, policy.window).thenReturn(count)
                } else {
                    Mono.just(count)
                }
            }
            .flatMap { count ->
                val remaining = (policy.maxRequests - count).coerceAtLeast(0)
                exchange.response.headers.set("X-RateLimit-Limit", policy.maxRequests.toString())
                exchange.response.headers.set("X-RateLimit-Remaining", remaining.toString())

                if (count <= policy.maxRequests) {
                    chain.filter(exchange)
                } else {
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    exchange.response.headers.set("Retry-After", windowSeconds.toString())
                    exchange.response.setComplete()
                }
            }
    }

    private fun isExcluded(path: String): Boolean {
        return path.startsWith("/actuator") ||
            path.startsWith("/internal") ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/webjars")
    }
}
