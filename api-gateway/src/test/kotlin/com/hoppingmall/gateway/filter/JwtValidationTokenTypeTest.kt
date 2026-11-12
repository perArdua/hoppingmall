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

@DisplayName("JwtValidation 필터의 토큰 종류 검사")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtValidationTokenTypeTest {

    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"
    private val factory = JwtValidationGatewayFilterFactory(secret)
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())
    private val anyRole = JwtValidationGatewayFilterFactory.Config()
    private val adminOnly = JwtValidationGatewayFilterFactory.Config().apply { requiredRole = "ADMIN" }

    private fun token(type: String?, role: String?): String =
        Jwts.builder()
            .setSubject("42")
            .apply { if (type != null) claim("typ", type) }
            .apply { if (role != null) claim("role", role) }
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    private fun exchangeWith(token: String): MockServerWebExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders")
                .header("Authorization", "Bearer $token")
                .build()
        )

    private fun filter(
        token: String,
        config: JwtValidationGatewayFilterFactory.Config = anyRole
    ): Pair<MockServerWebExchange, List<ServerWebExchange>> {
        val exchange = exchangeWith(token)
        val captured = mutableListOf<ServerWebExchange>()
        val chain = GatewayFilterChain { forwarded ->
            captured.add(forwarded)
            Mono.empty()
        }
        factory.apply(config).filter(exchange, chain).block()
        return exchange to captured
    }

    @Test
    fun typ이_refresh인_refresh_token을_Authorization에_넣으면_401이고_다음_필터로_넘기지_않는다() {
        val (exchange, forwarded) = filter(token(type = "refresh", role = null))

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(forwarded).isEmpty()
    }

    @Test
    fun typ_클레임이_없는_옛_refresh_token을_Authorization에_넣으면_401이고_다음_필터로_넘기지_않는다() {
        val (exchange, forwarded) = filter(token(type = null, role = null))

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(forwarded).isEmpty()
    }

    @Test
    fun typ이_refresh이면_role_클레임이_있어도_401이다() {
        val (exchange, forwarded) = filter(token(type = "refresh", role = "BUYER"))

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(forwarded).isEmpty()
    }

    @Test
    fun typ이_access도_refresh도_아닌_값이면_401이다() {
        val (exchange, forwarded) = filter(token(type = "id", role = "BUYER"))

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(forwarded).isEmpty()
    }

    @Test
    fun typ이_access여도_role_클레임이_없으면_BUYER로_채우지_않고_401이다() {
        val (exchange, forwarded) = filter(token(type = "access", role = null))

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(forwarded).isEmpty()
    }

    @Test
    fun 필수_역할이_ADMIN인_라우트에서도_refresh_token은_403이_아니라_401이다() {
        val (exchange, forwarded) = filter(token(type = "refresh", role = null), adminOnly)

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(forwarded).isEmpty()
    }

    @Test
    fun typ이_access이고_role이_있으면_신원_헤더를_붙여_다음_필터로_넘긴다() {
        val (exchange, forwarded) = filter(token(type = "access", role = "SELLER"))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(forwarded).hasSize(1)
        assertThat(forwarded[0].request.headers.getFirst("x-user-id")).isEqualTo("42")
        assertThat(forwarded[0].request.headers.getFirst("x-user-role")).isEqualTo("SELLER")
    }

    @Test
    fun typ_클레임이_없는_옛_access_token은_role이_있으면_그대로_통과한다() {
        val (exchange, forwarded) = filter(token(type = null, role = "BUYER"))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(forwarded).hasSize(1)
        assertThat(forwarded[0].request.headers.getFirst("x-user-id")).isEqualTo("42")
        assertThat(forwarded[0].request.headers.getFirst("x-user-role")).isEqualTo("BUYER")
    }
}
