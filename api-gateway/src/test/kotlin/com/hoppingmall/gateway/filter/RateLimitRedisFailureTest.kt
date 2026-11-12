package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.RateLimitKeyType
import com.hoppingmall.gateway.config.RateLimitPolicy
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration

@DisplayName("RateLimitGlobalFilter Redis 실패 처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RateLimitRedisFailureTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOps: ReactiveValueOperations<String, String>

    private val meterRegistry = SimpleMeterRegistry()

    private val policies = listOf(
        RateLimitPolicy("create-order", "/api/v1/orders", "POST", RateLimitKeyType.USER_ID, 5, Duration.ofMinutes(1))
    )

    private fun buildFilter() = RateLimitGlobalFilter(policies, redisTemplate, true, meterRegistry)

    private fun chainCapturing(captured: MutableList<ServerWebExchange>): GatewayFilterChain =
        GatewayFilterChain { exchange ->
            captured.add(exchange)
            Mono.empty()
        }

    private fun orderRequest(): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders").header("x-user-id", "42").build())

    private fun failureCount(operation: String): Double =
        meterRegistry.find("gateway.ratelimit.redis.failures")
            .tags("policy", "create-order", "operation", operation)
            .counter()
            ?.count() ?: 0.0

    @Test
    fun 카운트_증가가_실패하면_요청을_다음_필터로_넘기고_실패_지표를_올린다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.error(RedisConnectionFailureException("down")))
        val exchange = orderRequest()
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
        assertThat(exchange.response.statusCode).isNull()
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Limit")).isNull()
        assertThat(failureCount("increment")).isEqualTo(1.0)
    }

    @Test
    fun 첫_요청의_만료_설정이_실패하면_만료_없는_키를_지우고_요청을_넘긴다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(1L))
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.error(RedisConnectionFailureException("down")))
        whenever(redisTemplate.delete(any<String>())).thenReturn(Mono.just(1L))
        val exchange = orderRequest()
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(exchange, chainCapturing(captured)).block()

        val incrementedKey = argumentCaptor<String>()
        verify(valueOps).increment(incrementedKey.capture())
        verify(redisTemplate).delete(eq(incrementedKey.firstValue))
        assertThat(captured).hasSize(1)
        assertThat(failureCount("expire")).isEqualTo(1.0)
    }

    @Test
    fun 만료_설정과_키_삭제가_모두_실패해도_요청을_넘긴다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(1L))
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.error(RedisConnectionFailureException("down")))
        whenever(redisTemplate.delete(any<String>())).thenReturn(Mono.error(RedisConnectionFailureException("down")))
        val captured = mutableListOf<ServerWebExchange>()

        buildFilter().filter(orderRequest(), chainCapturing(captured)).block()

        assertThat(captured).hasSize(1)
        assertThat(failureCount("expire")).isEqualTo(1.0)
        assertThat(failureCount("delete")).isEqualTo(1.0)
    }

    @Test
    fun 뒷단_오류는_Redis_실패로_보지_않고_다음_필터를_한_번만_부른_채_그대로_전파한다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(2L))
        val calls = mutableListOf<ServerWebExchange>()
        val failingChain = GatewayFilterChain { exchange ->
            calls.add(exchange)
            Mono.error(IllegalStateException("backend down"))
        }

        assertThatThrownBy { buildFilter().filter(orderRequest(), failingChain).block() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("backend down")
        assertThat(calls).hasSize(1)
        assertThat(meterRegistry.find("gateway.ratelimit.redis.failures").counters()).isEmpty()
    }
}
