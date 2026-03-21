package com.hoppingmall.payment.refund.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class RefundCompletedEvent(
    val eventId: String = "",
    val refundId: Long = 0,
    val orderId: Long = 0,
    val paymentId: Long = 0,
    val buyerId: Long = 0,
    val refundAmount: BigDecimal = BigDecimal.ZERO,
    val pointRefundAmount: BigDecimal = BigDecimal.ZERO,
    val isFullRefund: Boolean = false,
    val couponId: Long? = null,
    val items: List<RefundItemEvent> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RefundItemEvent(
    val productId: Long = 0,
    val quantity: Int = 0,
    val refundPrice: BigDecimal = BigDecimal.ZERO
)
