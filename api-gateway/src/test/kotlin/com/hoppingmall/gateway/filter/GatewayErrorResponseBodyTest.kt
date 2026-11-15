package com.hoppingmall.gateway.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.gateway.config.GatewayRateLimitProperties
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

@DisplayName("게이트웨이가 직접 내는 401·403·429 응답")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GatewayErrorResponseBodyTest {

    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"
    private val objectMapper = ObjectMapper()
    private val passThrough = GatewayFilterChain { Mono.empty() }

    private fun token(role: String): String =
        Jwts.builder()
            .setSubject("7")
            .claim("typ", "access")
            .claim("role", role)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun MockServerWebExchange.body(): Map<String, Any?> =
        objectMapper.readValue(response.bodyAsString.block(), Map::class.java)
            .mapKeys { it.key.toString() }

    private fun rateLimitFilter(count: Long, clock: Clock): RateLimitGlobalFilter {
        val redisTemplate: ReactiveStringRedisTemplate = mock()
        val valueOps: ReactiveValueOperations<String, String> = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(count))
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
        return RateLimitGlobalFilter(
            listOf(RateLimitPolicy("create-order", "/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1))),
            redisTemplate,
            true,
            SimpleMeterRegistry(),
            GatewayRedisProperties(),
            GatewayRateLimitProperties(),
            clock
        )
    }

    private fun clockAt(epochMillis: Long): Clock = Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

    @Test
    fun 토큰_없이_부르면_401과_인증이_필요하다는_오류_본문을_준다() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders").build())

        JwtValidationGatewayFilterFactory(secret)
            .apply(JwtValidationGatewayFilterFactory.Config())
            .filter(exchange, passThrough)
            .block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exchange.response.headers.contentType).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(exchange.body()).isEqualTo(mapOf("code" to "A001", "message" to "인증이 필요합니다.", "data" to null))
    }

    @Test
    fun 잘못된_토큰이면_401과_같은_오류_본문을_준다() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders").header("Authorization", "Bearer not-a-jwt").build()
        )

        JwtValidationGatewayFilterFactory(secret)
            .apply(JwtValidationGatewayFilterFactory.Config())
            .filter(exchange, passThrough)
            .block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exchange.body()["code"]).isEqualTo("A001")
    }

    @Test
    fun 필수_역할이_모자라면_403과_권한이_없다는_오류_본문을_준다() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/admin/coupons").header("Authorization", "Bearer ${token("BUYER")}").build()
        )

        JwtValidationGatewayFilterFactory(secret)
            .apply(JwtValidationGatewayFilterFactory.Config().apply { requiredRole = "ADMIN" })
            .filter(exchange, passThrough)
            .block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(exchange.response.headers.contentType).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(exchange.body()).isEqualTo(mapOf("code" to "A002", "message" to "접근 권한이 없습니다.", "data" to null))
    }

    @Test
    fun 요청_한도를_넘기면_429와_요청이_너무_많다는_오류_본문을_준다() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders").build())

        rateLimitFilter(count = 6, clock = clockAt(1_800_000_000_000)).filter(exchange, passThrough).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(exchange.response.headers.contentType).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(exchange.body()).isEqualTo(
            mapOf("code" to "G001", "message" to "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", "data" to null)
        )
    }

    @ParameterizedTest
    @CsvSource(
        "0, 60",
        "15000, 45",
        "15001, 45",
        "59000, 1",
        "59999, 1"
    )
    fun Retry_After는_지금_창이_끝날_때까지_남은_초를_올림해_준다(millisIntoWindow: Long, expected: String) {
        val windowStart = 1_800_000_000_000 - 1_800_000_000_000 % 60_000
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders").build())

        rateLimitFilter(count = 6, clock = clockAt(windowStart + millisIntoWindow)).filter(exchange, passThrough).block()

        assertThat(exchange.response.headers.getFirst("Retry-After")).isEqualTo(expected)
    }

    @Test
    fun 요청_한도를_세는_창은_주입한_시계로_정한다() {
        val redisTemplate: ReactiveStringRedisTemplate = mock()
        val valueOps: ReactiveValueOperations<String, String> = mock()
        val keys = mutableListOf<String>()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenAnswer { keys.add(it.getArgument(0)); Mono.just(2L) }
        val filter = RateLimitGlobalFilter(
            listOf(RateLimitPolicy("create-order", "/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1))),
            redisTemplate,
            true,
            SimpleMeterRegistry(),
            GatewayRedisProperties(),
            GatewayRateLimitProperties(),
            clockAt(120_000)
        )

        filter.filter(
            MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders").header("x-user-id", "42").build()),
            passThrough
        ).block()

        assertThat(keys).containsExactly("rl:create-order:42:2")
    }
}
