package com.hoppingmall.payment.refund.dto.event

import java.math.BigDecimal

data class RefundCompletedEvent(
    val eventId: String,
    val refundId: Long,
    val orderId: Long,
    val paymentId: Long,
    val buyerId: Long,
    val refundAmount: BigDecimal,
    val pointRefundAmount: BigDecimal,
    val isFullRefund: Boolean,
    val couponId: Long?,
    val items: List<RefundItemEvent>
)

data class RefundItemEvent(
    val productId: Long,
    val quantity: Int,
    val refundPrice: BigDecimal
)
