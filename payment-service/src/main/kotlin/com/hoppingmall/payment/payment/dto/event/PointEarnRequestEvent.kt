package com.hoppingmall.payment.payment.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class PointEarnRequestEvent(
    val eventId: String = "",
    val userId: Long = 0,
    val orderId: Long = 0,
    val paymentId: Long = 0,
    val earnAmount: BigDecimal = BigDecimal.ZERO,
    val reason: String = "결제 완료"
)
