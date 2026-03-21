package com.hoppingmall.payment.payment.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentCompletedEvent(
    val paymentId: Long = 0,
    val orderId: Long = 0,
    val userId: Long = 0,
    val amount: BigDecimal = BigDecimal.ZERO,
    val pointAmount: BigDecimal = BigDecimal.ZERO,
    val method: PaymentMethod = PaymentMethod.CREDIT_CARD,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val transactionId: String = "",
    val completedAt: LocalDateTime = LocalDateTime.now()
)
