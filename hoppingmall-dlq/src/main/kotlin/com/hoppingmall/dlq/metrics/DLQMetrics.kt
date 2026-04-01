package com.hoppingmall.dlq.metrics

interface DLQMetrics {
    fun recordDlqSaved(topic: String)
    fun recordDlqRetrySuccess()
    fun recordDlqRetryFailed()
}
