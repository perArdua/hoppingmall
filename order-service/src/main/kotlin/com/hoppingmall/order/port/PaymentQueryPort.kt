package com.hoppingmall.order.port

import java.math.BigDecimal

interface PaymentQueryPort {
    fun findByOrderId(orderId: Long): PaymentInfo?
    fun findById(paymentId: Long): PaymentInfo?
}

data class PaymentInfo(
    val id: Long,
    val orderId: Long,
    val amount: BigDecimal,
    val pointAmount: BigDecimal,
    val couponId: Long?,
    val status: String
) {
    fun isSuccess(): Boolean = status == "SUCCESS"
}
