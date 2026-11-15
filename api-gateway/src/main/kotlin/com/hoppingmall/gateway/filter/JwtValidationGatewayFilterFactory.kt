package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.GatewayRedisProperties
import com.hoppingmall.gateway.config.GatewayRedisScheduler
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.Optional

@Component
class JwtValidationGatewayFilterFactory(
    @Value("\${jwt.secret}") secret: String,
    private val redisTemplate: ReactiveStringRedisTemplate? = null,
    private val meterRegistry: MeterRegistry = Metrics.globalRegistry,
    redisProperties: GatewayRedisProperties = GatewayRedisProperties()
) : AbstractGatewayFilterFactory<JwtValidationGatewayFilterFactory.Config>(Config::class.java) {

    private val key = Keys.hmacShaKeyFor(secret.toByteArray())
    private val redisCommandTimeout = redisProperties.commandTimeout

    private class AuthenticatedToken(
        val request: ServerHttpRequest,
        val userId: String,
        val issuedAtMillis: Long?
    )

    class Config {
        var requiredRole: String? = null
    }

    override fun shortcutFieldOrder(): List<String> = listOf("requiredRole")

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            val token = resolveToken(request)

            if (token == null) {
                return@GatewayFilter onUnauthorized(exchange)
            }

            Mono.fromCallable {
                val claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .body

                val userId = claims.subject
                val role = accessTokenRole(claims)
                    ?: return@fromCallable Optional.empty<AuthenticatedToken>()
                Optional.of(
                    AuthenticatedToken(
                        request = request.mutate()
                            .headers { it.remove("x-user-id"); it.remove("x-user-role") }
                            .header("x-user-id", userId)
                            .header("x-user-role", role)
                            .build(),
                        userId = userId,
                        issuedAtMillis = issuedAtMillis(claims)
                    )
                )
            }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorReturn(Optional.empty())
            .flatMap { authenticated ->
                if (authenticated.isEmpty) {
                    return@flatMap onUnauthorized(exchange)
                }
                val authenticatedToken = authenticated.get()
                Mono.zip(isLoggedOut(token), isIssuedBeforeInvalidation(authenticatedToken))
                    .flatMap { revoked ->
                        when {
                            revoked.t1 || revoked.t2 -> onUnauthorized(exchange)
                            !hasRequiredRole(authenticatedToken.request, config) -> onForbidden(exchange)
                            else -> chain.filter(exchange.mutate().request(authenticatedToken.request).build())
                        }
                    }
            }
        }
    }

    private fun isLoggedOut(token: String): Mono<Boolean> {
        val template = redisTemplate ?: return Mono.just(false)
        return Mono.defer { template.hasKey(BLACKLIST_KEY_PREFIX + token) }
            .subscribeOn(GatewayRedisScheduler.instance)
            .timeout(redisCommandTimeout)
            .defaultIfEmpty(false)
            .onErrorResume { e ->
                log.warn("로그아웃 토큰 Redis 조회 실패, 요청은 통과시킴: {}", e.message)
                meterRegistry.counter(BLACKLIST_REDIS_FAILURE_METRIC).increment()
                Mono.just(false)
            }
    }

    private fun isIssuedBeforeInvalidation(token: AuthenticatedToken): Mono<Boolean> {
        val template = redisTemplate ?: return Mono.just(false)
        return Mono.defer { template.opsForValue().get(INVALID_BEFORE_KEY_PREFIX + token.userId) }
            .subscribeOn(GatewayRedisScheduler.instance)
            .timeout(redisCommandTimeout)
            .map { value ->
                val invalidBefore = value.toLongOrNull()
                when {
                    invalidBefore == null -> {
                        log.warn("이전 발급 토큰 무효 시각 값이 숫자가 아님, 요청은 통과시킴 [userId={}]", token.userId)
                        false
                    }
                    else -> (token.issuedAtMillis ?: Long.MIN_VALUE) < invalidBefore
                }
            }
            .defaultIfEmpty(false)
            .onErrorResume { e ->
                log.warn("이전 발급 토큰 무효 시각 Redis 조회 실패, 요청은 통과시킴: {}", e.message)
                meterRegistry.counter(INVALID_BEFORE_REDIS_FAILURE_METRIC).increment()
                Mono.just(false)
            }
    }

    private fun issuedAtMillis(claims: Claims): Long? =
        (claims[ISSUED_AT_MS_CLAIM] as? Number)?.toLong() ?: claims.issuedAt?.time

    private fun accessTokenRole(claims: Claims): String? {
        val type = claims[TOKEN_TYPE_CLAIM]
        if (type != null && type != ACCESS_TOKEN_TYPE) return null
        return (claims[ROLE_CLAIM] as? String)?.takeIf { it.isNotBlank() }
    }

    private fun resolveToken(request: org.springframework.http.server.reactive.ServerHttpRequest): String? {
        val bearerToken = request.headers.getFirst("Authorization") ?: return null
        return if (bearerToken.startsWith("Bearer ")) bearerToken.substring(7) else null
    }

    private fun hasRequiredRole(request: org.springframework.http.server.reactive.ServerHttpRequest, config: Config): Boolean {
        val requiredRole = config.requiredRole ?: return true
        return request.headers.getFirst("x-user-role") == requiredRole
    }

    private fun onUnauthorized(exchange: ServerWebExchange): Mono<Void> =
        GatewayErrorResponse.UNAUTHORIZED.write(exchange)

    private fun onForbidden(exchange: ServerWebExchange): Mono<Void> =
        GatewayErrorResponse.FORBIDDEN.write(exchange)

    companion object {
        private val log = LoggerFactory.getLogger(JwtValidationGatewayFilterFactory::class.java)
        private const val BLACKLIST_KEY_PREFIX = "blacklist:"
        private const val BLACKLIST_REDIS_FAILURE_METRIC = "gateway.jwt.blacklist.redis.failures"
        private const val INVALID_BEFORE_KEY_PREFIX = "token-invalid-before:"
        private const val INVALID_BEFORE_REDIS_FAILURE_METRIC = "gateway.jwt.invalid-before.redis.failures"
        private const val ISSUED_AT_MS_CLAIM = "iat_ms"
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val ROLE_CLAIM = "role"
    }
}
