package com.hoppingmall.dlq.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("DefaultDLQMetrics")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DefaultDLQMetricsTest {

    private val meterRegistry: MeterRegistry = SimpleMeterRegistry()
    private val defaultDLQMetrics = DefaultDLQMetrics(meterRegistry)

    @Test
    fun DLQ_메시지_저장_카운터가_증가한다() {
        defaultDLQMetrics.recordDlqSaved("test-topic")

        val counter = meterRegistry.find("dlq.messages.saved").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun DLQ_메시지_저장_카운터가_여러번_증가한다() {
        defaultDLQMetrics.recordDlqSaved("topic-1")
        defaultDLQMetrics.recordDlqSaved("topic-2")
        defaultDLQMetrics.recordDlqSaved("topic-3")

        val counter = meterRegistry.find("dlq.messages.saved").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(3.0)
    }

    @Test
    fun DLQ_재시도_성공_카운터가_증가한다() {
        defaultDLQMetrics.recordDlqRetrySuccess()

        val counter = meterRegistry.find("dlq.retry.success").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun DLQ_재시도_실패_카운터가_증가한다() {
        defaultDLQMetrics.recordDlqRetryFailed()

        val counter = meterRegistry.find("dlq.retry.failed").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }
}
