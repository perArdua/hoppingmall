package com.hoppingmall.mall.payment.api

import java.math.BigDecimal

interface PaymentQueryPort {
    fun findById(paymentId: Long): PaymentInfo?
    fun findByOrderId(orderId: Long): PaymentInfo?
}

data class PaymentInfo(
    val id: Long,
    val orderId: Long,
    val amount: BigDecimal,
    val status: String,
    val isSuccess: Boolean
)
