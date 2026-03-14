package com.hoppingmall.product.statistics.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val pointAmount: BigDecimal,
    val transactionId: String,
    val completedAt: LocalDateTime
)
