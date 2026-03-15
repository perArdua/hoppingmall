package com.hoppingmall.order.order.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentCompletedEvent(
    val paymentId: Long = 0,
    val orderId: Long = 0,
    val userId: Long = 0,
    val amount: BigDecimal = BigDecimal.ZERO,
    val pointAmount: BigDecimal = BigDecimal.ZERO,
    val transactionId: String = ""
)
