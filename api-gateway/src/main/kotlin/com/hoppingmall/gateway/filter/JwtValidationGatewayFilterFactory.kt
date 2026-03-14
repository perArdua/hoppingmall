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

@Component
class JwtValidationGatewayFilterFactory(
    @Value("\${jwt.secret}") secret: String
) : AbstractGatewayFilterFactory<JwtValidationGatewayFilterFactory.Config>(Config::class.java) {

    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    class Config

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            val token = resolveToken(request)

            if (token == null) {
                return@GatewayFilter onUnauthorized(exchange)
            }

            try {
                val claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .body

                val userId = claims.subject
                val role = claims["role"] as? String ?: "BUYER"

                val modifiedRequest = request.mutate()
                    .header("x-user-id", userId)
                    .header("x-user-role", role)
                    .build()

                chain.filter(exchange.mutate().request(modifiedRequest).build())
            } catch (e: Exception) {
                onUnauthorized(exchange)
            }
        }
    }

    private fun resolveToken(request: org.springframework.http.server.reactive.ServerHttpRequest): String? {
        val bearerToken = request.headers.getFirst("Authorization") ?: return null
        return if (bearerToken.startsWith("Bearer ")) bearerToken.substring(7) else null
    }

    private fun onUnauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
