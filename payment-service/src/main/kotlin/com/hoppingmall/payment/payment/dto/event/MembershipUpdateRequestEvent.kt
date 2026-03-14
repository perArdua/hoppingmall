package com.hoppingmall.payment.payment.dto.event

import java.math.BigDecimal

data class MembershipUpdateRequestEvent(
    val eventId: String,
    val userId: Long,
    val orderId: Long,
    val paymentId: Long,
    val amount: BigDecimal
)
