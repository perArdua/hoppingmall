package com.hoppingmall.order.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class SagaCompensationMetrics(meterRegistry: MeterRegistry) {

    private val triggered: Counter = Counter.builder("saga.compensation.triggered")
        .description("Saga 보상 트랜잭션 트리거 수")
        .register(meterRegistry)

    private val success: Counter = Counter.builder("saga.compensation.success")
        .description("Saga 보상 자동 회복 성공 수")
        .register(meterRegistry)

    private val failed: Counter = Counter.builder("saga.compensation.failed")
        .description("Saga 보상 영구 실패 수")
        .register(meterRegistry)

    private val recoveryTime: Timer = Timer.builder("saga.compensation.recovery.time")
        .description("Saga 보상 회복 시간 분포")
        .publishPercentileHistogram()
        .register(meterRegistry)

    private val timeoutTriggered: Counter = Counter.builder("saga.timeout.triggered")
        .description("Saga 타임아웃 자동 보상 트리거 수")
        .register(meterRegistry)

    fun recordTriggered() = triggered.increment()
    fun recordSuccess() = success.increment()
    fun recordFailed() = failed.increment()
    fun recordRecoveryTime(durationMs: Long) = recoveryTime.record(durationMs, TimeUnit.MILLISECONDS)
    fun recordTimeoutTriggered() = timeoutTriggered.increment()
}
