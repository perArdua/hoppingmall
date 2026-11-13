package com.hoppingmall.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.Date

@DisplayName("JwtValidation 필터와 사용자 서비스가 발급한 토큰")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtValidationIssuedTokenTest {

    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"
    private val factory = JwtValidationGatewayFilterFactory(secret)
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())
    private val userId = 42L
    private val issuedAt = Date()

    private fun issuedAccessToken(role: String): String =
        Jwts.builder()
            .setSubject(userId.toString())
            .claim("typ", "access")
            .claim("role", role)
            .setIssuedAt(issuedAt)
            .setExpiration(Date(issuedAt.time + 60_000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    private fun issuedRefreshToken(): String =
        Jwts.builder()
            .setSubject(userId.toString())
            .claim("typ", "refresh")
            .setIssuedAt(issuedAt)
            .setExpiration(Date(issuedAt.time + 600_000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    private fun filter(token: String): Pair<MockServerWebExchange, List<ServerWebExchange>> {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders")
                .header("Authorization", "Bearer $token")
                .build()
        )
        val captured = mutableListOf<ServerWebExchange>()
        val chain = GatewayFilterChain { forwarded ->
            captured.add(forwarded)
            Mono.empty()
        }
        factory.apply(JwtValidationGatewayFilterFactory.Config()).filter(exchange, chain).block()
        return exchange to captured
    }

    @Test
    fun 로그인에서_함께_발급된_access_token은_신원_헤더를_붙여_다음_필터로_넘긴다() {
        val (exchange, forwarded) = filter(issuedAccessToken("BUYER"))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(forwarded).hasSize(1)
        assertThat(forwarded[0].request.headers.getFirst("x-user-id")).isEqualTo("42")
        assertThat(forwarded[0].request.headers.getFirst("x-user-role")).isEqualTo("BUYER")
    }

    @Test
    fun 로그인에서_함께_발급된_refresh_token은_401이고_다음_필터로_넘기지_않는다() {
        val (exchange, forwarded) = filter(issuedRefreshToken())

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(forwarded).isEmpty()
    }
}
