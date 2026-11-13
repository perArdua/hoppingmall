package com.hoppingmall.payment.payment.dto.event

import com.hoppingmall.payment.payment.enum.MembershipRevertReason
import java.math.BigDecimal

data class MembershipRevertRequestEvent(
    val eventId: String,
    val userId: Long,
    val orderId: Long,
    val paymentId: Long,
    val amount: BigDecimal,
    val reason: MembershipRevertReason,
    val refundId: Long? = null
)
