package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.GatewayRateLimitProperties
import com.hoppingmall.gateway.config.GatewayRedisProperties
import com.hoppingmall.gateway.config.GatewayRedisScheduler
import com.hoppingmall.gateway.config.RateLimitKeyType
import com.hoppingmall.gateway.config.RateLimitPolicy
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Optional

@Component
@Order(RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 1)
class RateLimitGlobalFilter(
    private val policies: List<RateLimitPolicy>,
    private val redisTemplate: ReactiveStringRedisTemplate,
    @Value("\${gateway.rate-limit.enabled:true}")
    private val enabled: Boolean,
    private val meterRegistry: MeterRegistry = Metrics.globalRegistry,
    redisProperties: GatewayRedisProperties = GatewayRedisProperties(),
    rateLimitProperties: GatewayRateLimitProperties = GatewayRateLimitProperties()
) : GlobalFilter {

    private val pathMatcher = AntPathMatcher()
    private val redisCommandTimeout = redisProperties.commandTimeout
    private val trustedProxyCount = rateLimitProperties.trustedProxyCount

    companion object {
        private val log = LoggerFactory.getLogger(RateLimitGlobalFilter::class.java)
        private const val REDIS_FAILURE_METRIC = "gateway.ratelimit.redis.failures"
        private const val X_FORWARDED_FOR = "X-Forwarded-For"

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
                    ?: resolveClientIp(exchange)
                    ?: "anonymous"
            }
            RateLimitKeyType.IP -> {
                resolveClientIp(exchange) ?: "anonymous"
            }
        }
    }

    private fun resolveClientIp(exchange: ServerWebExchange): String? {
        val remoteAddress = exchange.request.remoteAddress?.address?.hostAddress
        if (trustedProxyCount == 0) {
            return remoteAddress
        }
        val forwarded = exchange.request.headers[X_FORWARDED_FOR].orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (forwarded.isEmpty()) {
            return remoteAddress
        }
        return forwarded[(forwarded.size - trustedProxyCount).coerceAtLeast(0)]
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

        return Mono.defer { redisTemplate.opsForValue().increment(redisKey) }
            .subscribeOn(GatewayRedisScheduler.instance)
            .timeout(redisCommandTimeout)
            .flatMap { count ->
                if (count == 1L) {
                    setExpiry(redisKey, policy).thenReturn(count)
                } else {
                    Mono.just(count)
                }
            }
            .map { Optional.of(it) }
            .onErrorResume { e ->
                recordRedisFailure(policy, "increment", e)
                Mono.just(Optional.empty())
            }
            .flatMap { count ->
                if (count.isPresent) {
                    applyLimit(exchange, chain, policy, count.get())
                } else {
                    chain.filter(exchange)
                }
            }
    }

    private fun setExpiry(redisKey: String, policy: RateLimitPolicy): Mono<Boolean> =
        Mono.defer { redisTemplate.expire(redisKey, policy.window) }
            .subscribeOn(GatewayRedisScheduler.instance)
            .timeout(redisCommandTimeout)
            .onErrorResume { e ->
                recordRedisFailure(policy, "expire", e)
                Mono.defer { redisTemplate.delete(redisKey) }
                    .subscribeOn(GatewayRedisScheduler.instance)
                    .timeout(redisCommandTimeout)
                    .onErrorResume { deleteError ->
                        recordRedisFailure(policy, "delete", deleteError)
                        Mono.empty()
                    }
                    .thenReturn(false)
            }

    private fun applyLimit(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
        policy: RateLimitPolicy,
        count: Long
    ): Mono<Void> {
        val remaining = (policy.maxRequests - count).coerceAtLeast(0)
        exchange.response.headers.set("X-RateLimit-Limit", policy.maxRequests.toString())
        exchange.response.headers.set("X-RateLimit-Remaining", remaining.toString())

        if (count <= policy.maxRequests) {
            return chain.filter(exchange)
        }
        exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        exchange.response.headers.set("Retry-After", policy.window.seconds.toString())
        return exchange.response.setComplete()
    }

    private fun recordRedisFailure(policy: RateLimitPolicy, operation: String, e: Throwable) {
        log.warn("레이트리밋 Redis 호출 실패, 요청은 통과시킴 [policy={}, operation={}]: {}", policy.id, operation, e.message)
        Counter.builder(REDIS_FAILURE_METRIC)
            .tag("policy", policy.id)
            .tag("operation", operation)
            .register(meterRegistry)
            .increment()
    }

    private fun isExcluded(path: String): Boolean {
        return path.startsWith("/actuator") ||
            path.startsWith("/internal") ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/webjars")
    }
}
