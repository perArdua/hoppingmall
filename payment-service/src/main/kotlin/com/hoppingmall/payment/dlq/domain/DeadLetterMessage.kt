package com.hoppingmall.payment.dlq.domain

data class DeadLetterMessage(
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val originalKey: String?,
    val originalValue: String?,
    val exception: String?,
    val timestamp: Long
)
