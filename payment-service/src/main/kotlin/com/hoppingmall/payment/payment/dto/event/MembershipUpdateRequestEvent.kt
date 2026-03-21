package com.hoppingmall.payment.payment.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class MembershipUpdateRequestEvent(
    val eventId: String = "",
    val userId: Long = 0,
    val orderId: Long = 0,
    val paymentId: Long = 0,
    val amount: BigDecimal = BigDecimal.ZERO
)
