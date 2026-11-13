package com.hoppingmall.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.Date

@DisplayName("JwtValidation 필터의 로그아웃 토큰 검사")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class JwtValidationBlacklistTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    private val meterRegistry = SimpleMeterRegistry()
    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())
    private val anyRole = JwtValidationGatewayFilterFactory.Config()
    private val adminOnly = JwtValidationGatewayFilterFactory.Config().apply { requiredRole = "ADMIN" }

    private fun factory() = JwtValidationGatewayFilterFactory(secret, redisTemplate, meterRegistry)

    private fun accessToken(signingSecret: String = secret): String =
        Jwts.builder()
            .setSubject("42")
            .claim("typ", "access")
            .claim("role", "BUYER")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(signingSecret.toByteArray()), SignatureAlgorithm.HS256)
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
        factory().apply(config).filter(exchange, chain).block()
        return exchange to captured
    }

    private fun failureCount(): Double =
        meterRegistry.find("gateway.jwt.blacklist.redis.failures").counters().sumOf { it.count() }

    @Test
    fun 로그아웃으로_블랙리스트에_오른_access_token이면_401이고_다음_필터로_넘기지_않는다() {
        val token = accessToken()
        whenever(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(true))

        val (exchange, captured) = filter(token)

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 블랙리스트에_오른_토큰은_필수_역할이_맞지_않아도_403이_아니라_401이다() {
        val token = accessToken()
        whenever(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(true))

        val (exchange, captured) = filter(token, adminOnly)

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 블랙리스트에_없는_access_token은_user_service가_쓰는_키로_조회한_뒤_통과한다() {
        val token = accessToken()
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))

        val (exchange, captured) = filter(token)

        verify(redisTemplate).hasKey("blacklist:$token")
        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
        assertThat(captured[0].request.headers.getFirst("x-user-id")).isEqualTo("42")
        assertThat(failureCount()).isEqualTo(0.0)
    }

    @Test
    fun Redis_조회가_실패하면_요청을_통과시키고_실패_지표를_올린다() {
        val token = accessToken()
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.error(RedisConnectionFailureException("down")))

        val (exchange, captured) = filter(token)

        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
        assertThat(failureCount()).isEqualTo(1.0)
    }

    @Test
    fun Redis_조회가_값_없이_끝나면_블랙리스트에_없는_것으로_보고_통과시킨다() {
        val token = accessToken()
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.empty())

        val (exchange, captured) = filter(token)

        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
    }

    @Test
    fun 서명이_맞지_않는_토큰은_Redis를_조회하지_않고_401이다() {
        val token = accessToken("another-secret-key-for-testing-purposes-32chars!")

        val (exchange, captured) = filter(token)

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
        verifyNoInteractions(redisTemplate)
    }

    @Test
    fun 뒷단_오류는_Redis_실패로_보지_않고_다음_필터를_한_번만_부른_채_그대로_전파한다() {
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))
        val calls = mutableListOf<ServerWebExchange>()
        val failingChain = GatewayFilterChain { exchange ->
            calls.add(exchange)
            Mono.error(IllegalStateException("backend down"))
        }

        assertThatThrownBy { factory().apply(anyRole).filter(exchangeWith(accessToken()), failingChain).block() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("backend down")
        assertThat(calls).hasSize(1)
        assertThat(failureCount()).isEqualTo(0.0)
    }
}
