package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.GatewayRateLimitProperties
import com.hoppingmall.gateway.config.GatewayRedisProperties
import com.hoppingmall.gateway.config.RateLimitKeyType
import com.hoppingmall.gateway.config.RateLimitPolicy
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Duration

@DisplayName("레이트리밋 IP 키의 클라이언트 주소 고르기")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RateLimitClientIpTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOps: ReactiveValueOperations<String, String>

    private val policies = listOf(
        RateLimitPolicy("login", "/api/v1/users/login", "POST", RateLimitKeyType.IP, 5, Duration.ofMinutes(1)),
        RateLimitPolicy("create-order", "/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1))
    )

    private val chain = GatewayFilterChain { Mono.empty() }

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(2L))
    }

    private fun filter(trustedProxyCount: Int) = RateLimitGlobalFilter(
        policies,
        redisTemplate,
        true,
        SimpleMeterRegistry(),
        GatewayRedisProperties(),
        GatewayRateLimitProperties(trustedProxyCount)
    )

    private fun limitKeyOf(trustedProxyCount: Int, request: MockServerHttpRequest): String {
        filter(trustedProxyCount).filter(MockServerWebExchange.from(request), chain).block()
        val captor = argumentCaptor<String>()
        verify(valueOps).increment(captor.capture())
        return captor.firstValue.substringBeforeLast(':')
    }

    private fun login(vararg forwardedFor: String): MockServerHttpRequest =
        MockServerHttpRequest.post("/api/v1/users/login")
            .remoteAddress(InetSocketAddress("10.0.0.5", 40000))
            .apply { forwardedFor.forEach { header("X-Forwarded-For", it) } }
            .build()

    @Test
    fun 믿는_프록시_단수가_0이면_X_Forwarded_For를_보지_않고_연결_주소를_쓴다() {
        assertThat(limitKeyOf(0, login("203.0.113.7"))).isEqualTo("rl:login:10.0.0.5")
    }

    @Test
    fun 믿는_프록시_단수가_2면_오른쪽에서_두_번째_주소를_쓴다() {
        assertThat(limitKeyOf(2, login("198.51.100.1, 203.0.113.7, 10.0.0.9"))).isEqualTo("rl:login:203.0.113.7")
    }

    @Test
    fun 주소가_믿는_프록시_단수보다_적으면_가장_왼쪽_주소를_쓴다() {
        assertThat(limitKeyOf(3, login("203.0.113.7, 10.0.0.9"))).isEqualTo("rl:login:203.0.113.7")
    }

    @Test
    fun X_Forwarded_For_헤더가_여러_개면_순서대로_이어_붙여_오른쪽부터_센다() {
        assertThat(limitKeyOf(1, login("198.51.100.1", "203.0.113.7"))).isEqualTo("rl:login:203.0.113.7")
    }

    @Test
    fun 믿는_프록시가_있어도_X_Forwarded_For가_없으면_연결_주소를_쓴다() {
        assertThat(limitKeyOf(1, login())).isEqualTo("rl:login:10.0.0.5")
    }

    @Test
    fun X_Forwarded_For가_빈_값뿐이면_연결_주소를_쓴다() {
        assertThat(limitKeyOf(1, login(" , "))).isEqualTo("rl:login:10.0.0.5")
    }

    @Test
    fun 사용자_한도의_대체_키도_믿는_프록시_단수로_고른_주소를_쓴다() {
        val request = MockServerHttpRequest.post("/api/v1/orders")
            .remoteAddress(InetSocketAddress("10.0.0.5", 40000))
            .header("X-Forwarded-For", "198.51.100.1, 203.0.113.7")
            .build()

        assertThat(limitKeyOf(1, request)).isEqualTo("rl:create-order:203.0.113.7")
    }

    @Test
    fun 연결_주소도_X_Forwarded_For도_없으면_anonymous_키를_쓴다() {
        val request = MockServerHttpRequest.post("/api/v1/users/login").build()

        assertThat(limitKeyOf(1, request)).isEqualTo("rl:login:anonymous")
    }
}
