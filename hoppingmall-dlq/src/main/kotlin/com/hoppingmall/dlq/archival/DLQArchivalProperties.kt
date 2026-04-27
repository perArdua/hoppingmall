package com.hoppingmall.dlq.archival

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dlq.archival")
data class DLQArchivalProperties(
    val enabled: Boolean = false,
    val bucket: String = "dlq-archive",
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "minioadmin",
    val secretKey: String = "minioadmin",
    val region: String = "us-east-1",
    val retentionDays: Long = 90,
    val batchSize: Int = 100
)
