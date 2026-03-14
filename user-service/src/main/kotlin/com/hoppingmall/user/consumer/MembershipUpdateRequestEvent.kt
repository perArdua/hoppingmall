package com.hoppingmall.user.consumer

import java.math.BigDecimal

data class MembershipUpdateRequestEvent(
    val eventId: String,
    val userId: Long,
    val orderId: Long,
    val paymentId: Long,
    val amount: BigDecimal
)
