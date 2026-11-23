package com.hoppingmall.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.Date

@DisplayName("JwtValidation 필터의 이전 발급 토큰 무효 시각 검사")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtValidationInvalidBeforeTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOps: ReactiveValueOperations<String, String>

    private val meterRegistry = SimpleMeterRegistry()
    private val secret = "test-secret-key-for-testing-purposes-only-32chars!"
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))
    }

    private fun accessToken(issuedAtMillis: Long, issuedAtMsClaim: Long? = issuedAtMillis): String =
        Jwts.builder()
            .setSubject("42")
            .claim("typ", "access")
            .claim("role", "BUYER")
            .apply { if (issuedAtMsClaim != null) claim("iat_ms", issuedAtMsClaim) }
            .setIssuedAt(Date(issuedAtMillis))
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    private fun invalidBefore(value: String?) {
        whenever(valueOps.get("token-invalid-before:42")).thenReturn(if (value == null) Mono.empty() else Mono.just(value))
    }

    private fun filter(token: String): Pair<MockServerWebExchange, List<ServerWebExchange>> {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders").header("Authorization", "Bearer $token").build()
        )
        val captured = mutableListOf<ServerWebExchange>()
        JwtValidationGatewayFilterFactory(secret, redisTemplate, meterRegistry)
            .apply(JwtValidationGatewayFilterFactory.Config())
            .filter(exchange, GatewayFilterChain { forwarded -> captured.add(forwarded); Mono.empty() })
            .block()
        return exchange to captured
    }

    private fun failureCount(): Double =
        meterRegistry.find("gateway.jwt.invalid-before.redis.failures").counters().sumOf { it.count() }

    @Test
    fun 무효_시각_키가_없으면_통과한다() {
        invalidBefore(null)

        val (exchange, captured) = filter(accessToken(1_000_000_000_000))

        verify(valueOps).get("token-invalid-before:42")
        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
    }

    @Test
    fun 발급_밀리초가_무효_시각보다_작으면_401이고_다음_필터로_넘기지_않는다() {
        invalidBefore("1000000000500")

        val (exchange, captured) = filter(accessToken(1_000_000_000_499))

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exchange.response.bodyAsString.block()).contains("\"code\":\"A001\"")
        assertThat(captured).isEmpty()
    }

    @Test
    fun 발급_밀리초가_무효_시각과_같거나_크면_통과한다() {
        invalidBefore("1000000000500")

        val (sameExchange, sameCaptured) = filter(accessToken(1_000_000_000_500))
        val (laterExchange, laterCaptured) = filter(accessToken(1_000_000_000_501))

        assertThat(sameExchange.response.statusCode).isNull()
        assertThat(sameCaptured).hasSize(1)
        assertThat(laterExchange.response.statusCode).isNull()
        assertThat(laterCaptured).hasSize(1)
    }

    @Test
    fun 같은_초에_무효_시각_뒤에_발급된_토큰은_iat가_초_단위로_잘려도_iat_ms로_비교해_통과한다() {
        invalidBefore("1000000000500")

        val (exchange, captured) = filter(accessToken(issuedAtMillis = 1_000_000_000_700))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
    }

    @Test
    fun iat_ms가_없는_옛_토큰은_iat_곱하기_1000으로_비교해_무효_시각보다_작으면_401이다() {
        invalidBefore("1000000000500")

        val (exchange, captured) = filter(accessToken(issuedAtMillis = 1_000_000_000_700, issuedAtMsClaim = null))

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }

    @Test
    fun iat_ms가_없는_옛_토큰은_iat_곱하기_1000이_무효_시각과_같거나_크면_통과한다() {
        invalidBefore("1000000000000")

        val (exchange, captured) = filter(accessToken(issuedAtMillis = 1_000_000_000_999, issuedAtMsClaim = null))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
    }

    @Test
    fun Redis_조회가_실패하면_통과시키고_실패_지표를_올린다() {
        whenever(valueOps.get("token-invalid-before:42"))
            .thenReturn(Mono.error(RedisConnectionFailureException("down")))

        val (exchange, captured) = filter(accessToken(1_000_000_000_000))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
        assertThat(failureCount()).isEqualTo(1.0)
    }

    @Test
    fun Redis_조회가_시간_안에_끝나지_않으면_통과시키고_실패_지표를_올린다() {
        whenever(valueOps.get("token-invalid-before:42")).thenReturn(Mono.never())

        val (exchange, captured) = filter(accessToken(1_000_000_000_000))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
        assertThat(failureCount()).isEqualTo(1.0)
    }

    @Test
    fun 무효_시각_값이_숫자가_아니면_통과시킨다() {
        invalidBefore("not-a-number")

        val (exchange, captured) = filter(accessToken(1_000_000_000_000))

        assertThat(exchange.response.statusCode).isNull()
        assertThat(captured).hasSize(1)
    }

    @Test
    fun 블랙리스트에_오른_토큰은_무효_시각_키가_없어도_401이다() {
        val token = accessToken(1_000_000_000_000)
        whenever(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(true))
        invalidBefore(null)

        val (exchange, captured) = filter(token)

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(captured).isEmpty()
    }
}
