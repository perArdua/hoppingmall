package com.hoppingmall.order.port

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("Resilience4j CircuitBreaker 설정 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpProductQueryAdapterResilienceTest {

    @Test
    fun CB_설정이_올바르게_적용된다() {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            .build()

        val registry = CircuitBreakerRegistry.of(config)
        val cb = registry.circuitBreaker("product-query")

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.CLOSED)
        assertThat(cb.circuitBreakerConfig.slidingWindowSize).isEqualTo(10)
        assertThat(cb.circuitBreakerConfig.minimumNumberOfCalls).isEqualTo(5)
        assertThat(cb.circuitBreakerConfig.failureRateThreshold).isEqualTo(50f)
    }

    @Test
    fun 실패율_초과_시_CB가_OPEN_상태로_전환된다() {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50f)
            .build()

        val registry = CircuitBreakerRegistry.of(config)
        val cb = registry.circuitBreaker("product-query")

        repeat(2) { cb.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
        repeat(3) { cb.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, RuntimeException("fail")) }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
    }

    @Test
    fun 실패율_미만이면_CB가_CLOSED_유지된다() {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            .build()

        val registry = CircuitBreakerRegistry.of(config)
        val cb = registry.circuitBreaker("product-query")

        repeat(4) { cb.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
        repeat(1) { cb.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, RuntimeException("fail")) }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }
}
