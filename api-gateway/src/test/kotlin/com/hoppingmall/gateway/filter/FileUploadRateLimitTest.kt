package com.hoppingmall.gateway.filter

import com.hoppingmall.gateway.config.RateLimitConfig
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@DisplayName("파일 업로드 요청 제한")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class FileUploadRateLimitTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Mock
    private lateinit var valueOps: ReactiveValueOperations<String, String>

    private fun filter() = RateLimitGlobalFilter(RateLimitConfig().rateLimitPolicies(), redisTemplate, true)

    private fun upload(): MockServerWebExchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/files/upload").header("x-user-id", "7").build()
        )

    @Test
    fun 사용자당_분당_20번을_넘긴_파일_업로드는_429이고_뒷단에_닿지_않는다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(21L))
        val exchange = upload()
        val captured = mutableListOf<ServerWebExchange>()

        filter().filter(exchange, GatewayFilterChain { captured.add(it); Mono.empty() }).block()

        val key = argumentCaptor<String>()
        verify(valueOps).increment(key.capture())
        assertThat(key.firstValue).startsWith("rl:file-upload:7:")
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("20")
        assertThat(captured).isEmpty()
    }

    @Test
    fun 한도_안의_파일_업로드는_통과한다() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any())).thenReturn(Mono.just(20L))
        val exchange = upload()
        val captured = mutableListOf<ServerWebExchange>()

        filter().filter(exchange, GatewayFilterChain { captured.add(it); Mono.empty() }).block()

        assertThat(exchange.response.headers.getFirst("X-RateLimit-Remaining")).isEqualTo("0")
        assertThat(captured).hasSize(1)
    }
}
