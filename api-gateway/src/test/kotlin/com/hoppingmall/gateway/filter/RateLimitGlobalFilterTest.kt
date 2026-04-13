package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.RateLimitKeyType
import com.hoppingmall.gateway.config.RateLimitPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration

@DisplayName("RateLimitGlobalFilter")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RateLimitGlobalFilterTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOps: ReactiveValueOperations<String, String>

    private val policies = listOf(
        RateLimitPolicy("login", "/api/v1/users/login", "POST", RateLimitKeyType.IP, 5, Duration.ofMinutes(1)),
        RateLimitPolicy("signup", "/api/v1/users/signup", "POST", RateLimitKeyType.IP, 3, Duration.ofMinutes(1)),
        RateLimitPolicy("create-order", "/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1))
    )

    private fun buildFilter(enabled: Boolean = true) = RateLimitGlobalFilter(policies, redisTemplate, enabled)

    private fun chainCapturing(captured: MutableList<ServerWebExchange>): GatewayFilterChain =
        GatewayFilterChain { exchange ->
            captured.add(exchange)
            Mono.empty()
        }

    private fun mockRedisIncrement(count: Long) {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(count))
        if (count == 1L) {
            whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
        }
    }

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
    fun 허용된_요청이면_다음_필터로_전달한다() {
        mockRedisIncrement(1L)

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun 허용된_요청이면_레이트_리밋_헤더가_응답에_포함된다() {
        mockRedisIncrement(1L)

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("200")
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Remaining")).isEqualTo("199")
    }

    @Test
    fun 글로벌_한도를_초과하면_429를_반환한다() {
        mockRedisIncrement(201L)

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 한도_초과_시_Retry_After_헤더가_설정된다() {
        mockRedisIncrement(201L)

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)

        buildFilter().filter(exchange, chainCapturing(mutableListOf())).block()

        assertThat(exchange.response.headers.getFirst("Retry-After")).isEqualTo("60")
    }

    @Test
    fun 로그인_엔드포인트는_IP_기반_5회_제한이_적용된다() {
        mockRedisIncrement(6L)

        val request = MockServerHttpRequest.post("/api/v1/users/login").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 로그인_엔드포인트_한도_내_요청은_통과한다() {
        mockRedisIncrement(3L)

        val request = MockServerHttpRequest.post("/api/v1/users/login").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("5")
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Remaining")).isEqualTo("2")
    }

    @Test
    fun 회원가입_엔드포인트는_3회_제한이_적용된다() {
        mockRedisIncrement(4L)

        val request = MockServerHttpRequest.post("/api/v1/users/signup").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(captured).isEmpty()
    }

    @Test
    fun 정책에_없는_GET_요청은_글로벌_폴백이_적용된다() {
        mockRedisIncrement(50L)

        val request = MockServerHttpRequest.get("/api/v1/notifications").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("200")
    }

    @Test
    fun enabled가_false이면_모든_요청을_통과시킨다() {
        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter(enabled = false).filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
        verify(redisTemplate, never()).opsForValue()
    }

    @Test
    fun 첫_번째_요청이면_Redis_TTL을_설정한다() {
        mockRedisIncrement(1L)

        val request = MockServerHttpRequest.get("/api/v1/products").build()
        val exchange = MockServerWebExchange.from(request)

        buildFilter().filter(exchange, chainCapturing(mutableListOf())).block()

        verify(redisTemplate).expire(any(), eq(Duration.ofMinutes(1)))
    }

    @Test
    fun POST_주문_엔드포인트는_USER_ID_기반_5회_제한이_적용된다() {
        mockRedisIncrement(6L)

        val request = MockServerHttpRequest.post("/api/v1/orders")
            .header("x-user-id", "42")
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("5")
    }

    @Test
    fun GET_주문_엔드포인트는_글로벌_폴백이_적용된다() {
        mockRedisIncrement(10L)

        val request = MockServerHttpRequest.get("/api/v1/orders").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("200")
    }
}
