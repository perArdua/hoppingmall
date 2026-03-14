package com.hoppingmall.product.statistics.port

import java.math.BigDecimal

interface OrderItemQueryPort {
    fun findByOrderId(orderId: Long): List<OrderItemInfo>
}

data class OrderItemInfo(
    val id: Long,
    val orderId: Long,
    val productId: Long,
    val quantity: Int,
    val totalPrice: BigDecimal
)
