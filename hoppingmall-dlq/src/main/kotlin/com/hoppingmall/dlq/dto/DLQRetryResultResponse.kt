package com.hoppingmall.dlq.dto

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DLQRetryResultResponse(
    val topic: String,
    val totalAttempted: Int,
    val successCount: Int,
    val failureCount: Int,
    val errors: List<String>
)
