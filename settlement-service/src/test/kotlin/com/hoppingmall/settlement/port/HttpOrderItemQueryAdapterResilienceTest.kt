package com.hoppingmall.settlement.port

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.time.Duration

@DisplayName("Resilience4j CircuitBreaker 정산 설정 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpOrderItemQueryAdapterResilienceTest {

    @Test
    fun 정산_CB_설정이_기본과_다르게_적용된다() {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .slowCallDurationThreshold(Duration.ofSeconds(8))
            .build()

        val registry = CircuitBreakerRegistry.of(config)
        val cb = registry.circuitBreaker("order-item-query")

        assertThat(cb.circuitBreakerConfig.slidingWindowSize).isEqualTo(5)
        assertThat(cb.circuitBreakerConfig.minimumNumberOfCalls).isEqualTo(3)
        assertThat(cb.circuitBreakerConfig.waitIntervalFunctionInOpenState.apply(1)).isEqualTo(Duration.ofSeconds(60).toMillis())
        assertThat(cb.circuitBreakerConfig.slowCallDurationThreshold).isEqualTo(Duration.ofSeconds(8))
    }

    @Test
    fun 정산_CB_실패율_초과_시_OPEN_전환() {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(50f)
            .build()

        val registry = CircuitBreakerRegistry.of(config)
        val cb = registry.circuitBreaker("order-item-query")

        repeat(1) { cb.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
        repeat(3) { cb.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS, RuntimeException("fail")) }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
    }
}
