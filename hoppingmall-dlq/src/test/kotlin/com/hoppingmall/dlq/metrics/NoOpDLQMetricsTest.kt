package com.hoppingmall.dlq.metrics

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("NoOpDLQMetrics")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NoOpDLQMetricsTest {

    private val noOpDLQMetrics = NoOpDLQMetrics()

    @Test
    fun recordDlqSaved_호출해도_예외가_발생하지_않는다() {
        noOpDLQMetrics.recordDlqSaved("test-topic")
    }

    @Test
    fun recordDlqRetrySuccess_호출해도_예외가_발생하지_않는다() {
        noOpDLQMetrics.recordDlqRetrySuccess()
    }

    @Test
    fun recordDlqRetryFailed_호출해도_예외가_발생하지_않는다() {
        noOpDLQMetrics.recordDlqRetryFailed()
    }
}
