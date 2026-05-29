package com.hoppingmall.order.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("SagaCompensationMetrics")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SagaCompensationMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = SagaCompensationMetrics(registry)

    @Test
    fun 보상_트리거_카운터를_증가시킨다() {
        metrics.recordTriggered()
        metrics.recordTriggered()

        assertThat(registry.counter("saga.compensation.triggered").count()).isEqualTo(2.0)
    }

    @Test
    fun 보상_성공_카운터를_증가시킨다() {
        metrics.recordSuccess()

        assertThat(registry.counter("saga.compensation.success").count()).isEqualTo(1.0)
    }

    @Test
    fun 보상_실패_카운터를_증가시킨다() {
        metrics.recordFailed()

        assertThat(registry.counter("saga.compensation.failed").count()).isEqualTo(1.0)
    }

    @Test
    fun 타임아웃_보상_트리거_카운터를_증가시킨다() {
        metrics.recordTimeoutTriggered()

        assertThat(registry.counter("saga.timeout.triggered").count()).isEqualTo(1.0)
    }

    @Test
    fun 회복_시간을_타이머에_기록한다() {
        metrics.recordRecoveryTime(120)

        val timer = registry.timer("saga.compensation.recovery.time")
        assertThat(timer.count()).isEqualTo(1L)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(120.0)
    }
}
