package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.GatewayRedisProperties
import com.hoppingmall.gateway.config.RateLimitKeyType
import com.hoppingmall.gateway.config.RateLimitPolicy
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Date

@DisplayName("게이트웨이 Redis 호출의 시간 제한")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RedisCommandTimeoutTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOps: ReactiveValueOperations<String, String>

    private val meterRegistry = SimpleMeterRegistry()
    private val shortTimeout = GatewayRedisProperties(commandTimeout = Duration.ofMillis(50))
    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"

    private val policies = listOf(
        RateLimitPolicy("create-order", "/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1))
    )

    private fun rateLimitFilter() = RateLimitGlobalFilter(policies, redisTemplate, true, meterRegistry, shortTimeout)

    private fun chainCapturing(captured: MutableList<ServerWebExchange>): GatewayFilterChain =
        GatewayFilterChain { exchange ->
            captured.add(exchange)
            Mono.empty()
        }

    private fun orderRequest(): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders").header("x-user-id", "42").build())

    private fun rateLimitFailures(operation: String): Double =
        meterRegistry.find("gateway.ratelimit.redis.failures")
            .tags("policy", "create-order", "operation", operation)
            .counter()
            ?.count() ?: 0.0

    private fun accessToken(): String =
        Jwts.builder()
            .setSubject("42")
            .claim("typ", "access")
            .claim("role", "BUYER")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @Test
    fun 기본_시간_제한은_500ms다() {
        assertThat(GatewayRedisProperties().commandTimeout).isEqualTo(Duration.ofMillis(500))
    }

    @Test
    fun 카운트_증가가_시간_제한_안에_끝나지_않으면_요청을_넘기고_실패_지표를_올린다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.never())
        val exchange = orderRequest()
        val captured = mutableListOf<ServerWebExchange>()

        rateLimitFilter().filter(exchange, chainCapturing(captured)).block(Duration.ofSeconds(2))

        assertThat(captured).hasSize(1)
        assertThat(exchange.response.statusCode).isNull()
        assertThat(rateLimitFailures("increment")).isEqualTo(1.0)
    }

    @Test
    fun 만료_설정이_시간_제한_안에_끝나지_않으면_키를_지우고_요청을_넘긴다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(1L))
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.never())
        whenever(redisTemplate.delete(any<String>())).thenReturn(Mono.never())
        val captured = mutableListOf<ServerWebExchange>()

        rateLimitFilter().filter(orderRequest(), chainCapturing(captured)).block(Duration.ofSeconds(2))

        verify(redisTemplate).delete(any<String>())
        assertThat(captured).hasSize(1)
        assertThat(rateLimitFailures("expire")).isEqualTo(1.0)
        assertThat(rateLimitFailures("delete")).isEqualTo(1.0)
    }

    @Test
    fun 블랙리스트_조회가_시간_제한_안에_끝나지_않으면_요청을_넘기고_실패_지표를_올린다() {
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.never())
        val factory = JwtValidationGatewayFilterFactory(secret, redisTemplate, meterRegistry, shortTimeout)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders").header("Authorization", "Bearer ${accessToken()}").build()
        )
        val captured = mutableListOf<ServerWebExchange>()

        factory.apply(JwtValidationGatewayFilterFactory.Config())
            .filter(exchange, chainCapturing(captured))
            .block(Duration.ofSeconds(2))

        assertThat(captured).hasSize(1)
        assertThat(captured.single().request.headers.getFirst("x-user-id")).isEqualTo("42")
        assertThat(meterRegistry.find("gateway.jwt.blacklist.redis.failures").counters().sumOf { it.count() })
            .isEqualTo(1.0)
    }
}
