package com.hoppingmall.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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

@DisplayName("JwtValidationGatewayFilterFactory")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtValidationGatewayFilterFactoryTest {

    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"
    private val factory = JwtValidationGatewayFilterFactory(secret)
    private val config = JwtValidationGatewayFilterFactory.Config()
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    private fun chainCapturing(captured: MutableList<ServerWebExchange>): GatewayFilterChain =
        GatewayFilterChain { exchange ->
            captured.add(exchange)
            Mono.empty()
        }

    private fun buildToken(subject: String, role: String, expiry: Date = Date(System.currentTimeMillis() + 60_000)): String {
        return Jwts.builder()
            .setSubject(subject)
            .claim("role", role)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    @Test
    fun Authorization_헤더가_없으면_401을_반환한다() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun Bearer_접두사가_없는_토큰이면_401을_반환한다() {
        val token = buildToken("user1", "BUYER")
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", token)
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 유효한_JWT_토큰이면_다음_필터로_전달하고_x_user_id_헤더를_설정한다() {
        val token = buildToken("42", "BUYER")
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isNotEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).hasSize(1)
        assertThat(captured[0].request.headers.getFirst("x-user-id")).isEqualTo("42")
    }

    @Test
    fun 유효한_JWT_토큰이면_role_클레임이_x_user_role_헤더에_설정된다() {
        val token = buildToken("10", "SELLER")
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(captured[0].request.headers.getFirst("x-user-role")).isEqualTo("SELLER")
    }

    @Test
    fun role_클레임이_없으면_기본값_BUYER가_x_user_role_헤더에_설정된다() {
        val tokenWithoutRole = Jwts.builder()
            .setSubject("99")
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer $tokenWithoutRole")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(captured[0].request.headers.getFirst("x-user-role")).isEqualTo("BUYER")
    }

    @Test
    fun 잘못된_서명의_JWT_토큰이면_401을_반환한다() {
        val wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-for-testing-purposes-only-32chars!".toByteArray())
        val badToken = Jwts.builder()
            .setSubject("user")
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(wrongKey, SignatureAlgorithm.HS256)
            .compact()
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer $badToken")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 만료된_JWT_토큰이면_401을_반환한다() {
        val expiredToken = buildToken("user", "BUYER", Date(System.currentTimeMillis() - 1_000))
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer $expiredToken")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 요청에_이미_x_user_id_헤더가_있으면_토큰_값으로_교체된다() {
        val token = buildToken("real-user", "ADMIN")
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer $token")
            .header("x-user-id", "injected-id")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(captured[0].request.headers.getFirst("x-user-id")).isEqualTo("real-user")
    }

    @Test
    fun 요청에_이미_x_user_role_헤더가_있으면_토큰_값으로_교체된다() {
        val token = buildToken("user", "ADMIN")
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer $token")
            .header("x-user-role", "BUYER")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(captured[0].request.headers.getFirst("x-user-role")).isEqualTo("ADMIN")
    }

    @Test
    fun 완전히_잘못된_형식의_토큰이면_401을_반환한다() {
        val request = MockServerHttpRequest.get("/api/test")
            .header("Authorization", "Bearer not.a.jwt")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(config).filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }
}
