package com.hoppingmall.mall.order.dto.response

import com.hoppingmall.mall.order.domain.OrderItem
import java.math.BigDecimal

data class OrderItemResponse(
    val id: Long,
    val sellerId: Long,
    val productId: Long,
    val productName: String,
    val productPrice: BigDecimal,
    val quantity: Int,
    val totalPrice: BigDecimal
) {
    companion object {
        fun from(orderItem: OrderItem): OrderItemResponse {
            return OrderItemResponse(
                id = orderItem.id!!,
                sellerId = orderItem.sellerId,
                productId = orderItem.productId,
                productName = orderItem.productName,
                productPrice = orderItem.productPrice,
                quantity = orderItem.quantity,
                totalPrice = orderItem.totalPrice
            )
        }
    }
}
