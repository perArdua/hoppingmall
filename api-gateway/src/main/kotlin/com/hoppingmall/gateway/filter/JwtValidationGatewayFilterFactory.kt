package com.hoppingmall.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.Optional

@Component
class JwtValidationGatewayFilterFactory(
    @Value("\${jwt.secret}") secret: String
) : AbstractGatewayFilterFactory<JwtValidationGatewayFilterFactory.Config>(Config::class.java) {

    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

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
                val role = claims["role"] as? String ?: "BUYER"
                Optional.of(
                    request.mutate()
                        .headers { it.remove("x-user-id"); it.remove("x-user-role") }
                        .header("x-user-id", userId)
                        .header("x-user-role", role)
                        .build()
                )
            }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorReturn(Optional.empty())
            .flatMap { authenticatedRequest ->
                when {
                    authenticatedRequest.isEmpty -> onUnauthorized(exchange)
                    !hasRequiredRole(authenticatedRequest.get(), config) -> onForbidden(exchange)
                    else -> chain.filter(exchange.mutate().request(authenticatedRequest.get()).build())
                }
            }
        }
    }

    private fun resolveToken(request: org.springframework.http.server.reactive.ServerHttpRequest): String? {
        val bearerToken = request.headers.getFirst("Authorization") ?: return null
        return if (bearerToken.startsWith("Bearer ")) bearerToken.substring(7) else null
    }

    private fun hasRequiredRole(request: org.springframework.http.server.reactive.ServerHttpRequest, config: Config): Boolean {
        val requiredRole = config.requiredRole ?: return true
        return request.headers.getFirst("x-user-role") == requiredRole
    }

    private fun onUnauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    private fun onForbidden(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        return exchange.response.setComplete()
    }
}
