package com.hoppingmall.dlq.dto

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DLQStatsResponse(
    val totalMessages: Long,
    val pendingCount: Long,
    val processedCount: Long,
    val failedCount: Long,
    val topicStats: List<DLQTopicStatsResponse>,
    val lastUpdated: Long
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DLQTopicStatsResponse(
    val topic: String,
    val total: Long,
    val pending: Long,
    val processed: Long,
    val failed: Long
)
