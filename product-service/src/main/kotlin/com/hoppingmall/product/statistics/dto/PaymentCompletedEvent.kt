package com.hoppingmall.product.statistics.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentCompletedEvent(
    val paymentId: Long = 0,
    val orderId: Long = 0,
    val userId: Long = 0,
    val amount: BigDecimal = BigDecimal.ZERO,
    val pointAmount: BigDecimal = BigDecimal.ZERO,
    val transactionId: String = "",
    val completedAt: LocalDateTime = LocalDateTime.now()
)
