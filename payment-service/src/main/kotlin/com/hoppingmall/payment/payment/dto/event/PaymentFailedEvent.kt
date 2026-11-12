package com.hoppingmall.payment.payment.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
data class PaymentFailedEvent(
    val eventId: String = "",
    val paymentId: Long = 0,
    val orderId: Long = 0,
    val userId: Long = 0,
    val amount: BigDecimal = BigDecimal.ZERO,
    val reason: String = ""
)
