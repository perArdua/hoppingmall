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

@DisplayName("JwtValidation 필터의 필수 역할")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtValidationRequiredRoleTest {

    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"
    private val factory = JwtValidationGatewayFilterFactory(secret)
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())
    private val adminOnly = JwtValidationGatewayFilterFactory.Config().apply { requiredRole = "ADMIN" }

    private fun chainCapturing(captured: MutableList<ServerWebExchange>): GatewayFilterChain =
        GatewayFilterChain { exchange ->
            captured.add(exchange)
            Mono.empty()
        }

    private fun exchangeWith(token: String): MockServerWebExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/v3/api-docs/order-service")
                .header("Authorization", "Bearer $token")
                .build()
        )

    private fun tokenWithRole(role: String?): String =
        Jwts.builder()
            .setSubject("7")
            .apply { if (role != null) claim("role", role) }
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    @Test
    fun 필수_역할이_ADMIN인데_BUYER_토큰이면_403이고_다음_필터로_넘기지_않는다() {
        val exchange = exchangeWith(tokenWithRole("BUYER"))
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(adminOnly).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 필수_역할이_ADMIN인데_role_클레임이_없으면_BUYER로_보고_403이다() {
        val exchange = exchangeWith(tokenWithRole(null))
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(adminOnly).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 필수_역할이_ADMIN이고_ADMIN_토큰이면_신원_헤더를_붙여_다음_필터로_넘긴다() {
        val exchange = exchangeWith(tokenWithRole("ADMIN"))
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(adminOnly).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isNotEqualTo(HttpStatus.FORBIDDEN)
        assertThat(captured).hasSize(1)
        assertThat(captured[0].request.headers.getFirst("x-user-id")).isEqualTo("7")
        assertThat(captured[0].request.headers.getFirst("x-user-role")).isEqualTo("ADMIN")
    }

    @Test
    fun 필수_역할이_있어도_토큰이_없으면_401이다() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v3/api-docs/order-service").build())
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(adminOnly).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 필수_역할을_짧은_설정_한_칸으로_받는다() {
        assertThat(factory.shortcutFieldOrder()).containsExactly("requiredRole")
    }
}
