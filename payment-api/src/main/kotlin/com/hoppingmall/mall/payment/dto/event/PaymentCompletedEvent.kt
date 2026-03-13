package com.hoppingmall.mall.payment.dto.event

import java.math.BigDecimal
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import java.time.LocalDateTime

data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val pointAmount: BigDecimal,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val transactionId: String,
    val completedAt: LocalDateTime
) 
