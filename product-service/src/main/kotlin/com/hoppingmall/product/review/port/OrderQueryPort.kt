package com.hoppingmall.product.review.port

import java.math.BigDecimal

interface OrderQueryPort {
    fun isDelivered(orderId: Long, buyerId: Long): Boolean
    fun findOrderItemById(orderItemId: Long): OrderItemInfo?
    fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo>
}

data class OrderItemInfo(
    val id: Long,
    val orderId: Long,
    val sellerId: Long,
    val productId: Long,
    val productName: String,
    val productPrice: BigDecimal,
    val quantity: Int,
    val totalPrice: BigDecimal
)
