package com.hoppingmall.mall.refund.dto.event

import java.math.BigDecimal
import java.util.UUID

data class RefundCompletedEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val refundId: Long,
    val orderId: Long,
    val paymentId: Long,
    val buyerId: Long,
    val refundAmount: BigDecimal,
    val pointRefundAmount: BigDecimal,
    val isFullRefund: Boolean,
    val items: List<RefundItemEvent>
)

data class RefundItemEvent(
    val productId: Long,
    val quantity: Int,
    val refundPrice: BigDecimal
)
