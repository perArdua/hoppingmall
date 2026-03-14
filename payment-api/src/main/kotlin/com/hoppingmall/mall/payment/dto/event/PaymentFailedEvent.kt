package com.hoppingmall.mall.payment.dto.event

import java.math.BigDecimal

data class PaymentFailedEvent(
    val eventId: String,
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val reason: String
)
