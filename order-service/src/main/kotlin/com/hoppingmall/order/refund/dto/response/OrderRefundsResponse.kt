package com.hoppingmall.order.refund.dto.response

data class OrderRefundsResponse(
    val orderId: Long,
    val items: List<OrderItemRefundResponse>,
    val refunds: List<RefundResponse>
)

data class OrderItemRefundResponse(
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val refundedQuantity: Int,
    val inProgressQuantity: Int,
    val refundableQuantity: Int
)
