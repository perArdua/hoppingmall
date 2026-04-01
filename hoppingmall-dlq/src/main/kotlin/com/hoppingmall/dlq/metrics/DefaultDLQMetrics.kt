package com.hoppingmall.dlq.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(MeterRegistry::class)
class DefaultDLQMetrics(
    meterRegistry: MeterRegistry
) : DLQMetrics {

    private val dlqSavedCounter: Counter = Counter.builder("dlq.messages.saved")
        .description("DLQ에 저장된 메시지 수")
        .register(meterRegistry)

    private val dlqRetrySuccessCounter: Counter = Counter.builder("dlq.retry.success")
        .description("DLQ 재시도 성공 수")
        .register(meterRegistry)

    private val dlqRetryFailedCounter: Counter = Counter.builder("dlq.retry.failed")
        .description("DLQ 재시도 실패 수")
        .register(meterRegistry)

    override fun recordDlqSaved(topic: String) {
        dlqSavedCounter.increment()
    }

    override fun recordDlqRetrySuccess() {
        dlqRetrySuccessCounter.increment()
    }

    override fun recordDlqRetryFailed() {
        dlqRetryFailedCounter.increment()
    }
}
