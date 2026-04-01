package com.hoppingmall.dlq.metrics

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(DefaultDLQMetrics::class)
class NoOpDLQMetrics : DLQMetrics {
    override fun recordDlqSaved(topic: String) {}
    override fun recordDlqRetrySuccess() {}
    override fun recordDlqRetryFailed() {}
}
