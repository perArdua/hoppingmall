package com.hoppingmall.dlq.archival

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dlq.retention")
data class DLQRetentionProperties(
    val days: Long = 30,
    val purgeMaxBatches: Int = 1000
)
