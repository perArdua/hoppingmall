package com.hoppingmall.dlq.config

import com.hoppingmall.dlq.domain.DLQMessage
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class DLQRetryBackoffConfig(
    @Value("\${dlq.retry.backoff-ms:60000,300000,1800000}") private val backoffMs: LongArray
) {
    @PostConstruct
    fun apply() {
        require(backoffMs.isNotEmpty()) { "dlq.retry.backoff-ms must not be empty" }
        DLQMessage.backoffIntervalsMs = backoffMs
    }
}
