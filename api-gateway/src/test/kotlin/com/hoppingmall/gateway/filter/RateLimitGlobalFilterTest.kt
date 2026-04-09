package com.hoppingmall.gateway.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@DisplayName("RateLimitGlobalFilter")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RateLimitGlobalFilterTest {

    @Mock
    private lateinit var keyResolver: KeyResolver

    @Mock
    private lateinit var redisRateLimiter: RedisRateLimiter

    private fun buildFilter() = RateLimitGlobalFilter(keyResolver, redisRateLimiter)

    private fun chainCapturing(captured: MutableList<ServerWebExchange>): GatewayFilterChain =
        GatewayFilterChain { exchange ->
            captured.add(exchange)
            Mono.empty()
        }

    private fun allowedResponse(): RateLimiter.Response =
        RateLimiter.Response(true, mapOf("X-RateLimit-Remaining" to "49"))

    private fun deniedResponse(): RateLimiter.Response =
        RateLimiter.Response(false, emptyMap())

    @Test
    fun actuator_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.get("/actuator/health").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun internal_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.get("/internal/service").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun swagger_ui_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.get("/swagger-ui/index.html").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun v3_api_docs_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.get("/v3/api-docs/swagger-config").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun webjars_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.get("/webjars/swagger-ui/bundle.js").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun 회원가입_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.post("/api/v1/users/signup").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun 로그인_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.post("/api/v1/users/login").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun 토큰_갱신_경로는_레이트_리밋을_건너뛴다() {
        val request = MockServerHttpRequest.post("/api/v1/auth/refresh").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun 허용된_요청이면_다음_필터로_전달한다() {
        whenever(keyResolver.resolve(any())).thenReturn(Mono.just("user:1"))
        whenever(redisRateLimiter.isAllowed(any(), any())).thenReturn(Mono.just(allowedResponse()))

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun 허용된_요청이면_레이트_리밋_헤더가_응답에_포함된다() {
        whenever(keyResolver.resolve(any())).thenReturn(Mono.just("user:1"))
        whenever(redisRateLimiter.isAllowed(any(), any())).thenReturn(Mono.just(allowedResponse()))

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured[0].response.headers["X-RateLimit-Remaining"]).containsExactly("49")
    }

    @Test
    fun 허용_한도를_초과하면_429를_반환한다() {
        whenever(keyResolver.resolve(any())).thenReturn(Mono.just("user:1"))
        whenever(redisRateLimiter.isAllowed(any(), any())).thenReturn(Mono.just(deniedResponse()))

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 허용_한도를_초과하면_Retry_After_헤더가_1로_설정된다() {
        whenever(keyResolver.resolve(any())).thenReturn(Mono.just("user:1"))
        whenever(redisRateLimiter.isAllowed(any(), any())).thenReturn(Mono.just(deniedResponse()))

        val request = MockServerHttpRequest.get("/api/v1/orders").build()
        val exchange = MockServerWebExchange.from(request)

        buildFilter().filter(exchange, chainCapturing(mutableListOf())).block()

        assertThat(exchange.response.headers.getFirst("Retry-After")).isEqualTo("1")
    }
}
