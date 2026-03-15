package com.hoppingmall.order.order.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentFailedEvent(
    val eventId: String = "",
    val paymentId: Long = 0,
    val orderId: Long = 0,
    val userId: Long = 0,
    val amount: BigDecimal = BigDecimal.ZERO,
    val reason: String = ""
)
