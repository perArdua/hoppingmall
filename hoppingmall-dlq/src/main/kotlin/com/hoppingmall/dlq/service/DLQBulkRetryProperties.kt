package com.hoppingmall.dlq.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dlq.admin")
data class DLQBulkRetryProperties(
    val bulkRetryMaxCount: Int = 100
)
