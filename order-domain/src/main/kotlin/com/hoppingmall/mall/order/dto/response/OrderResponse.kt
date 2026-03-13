package com.hoppingmall.mall.order.dto.response

import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.enum.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderResponse(
    val id: Long,
    val buyerId: Long,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val items: List<OrderItemResponse>,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(order: Order, orderItems: List<OrderItem>): OrderResponse {
            return OrderResponse(
                id = order.id!!,
                buyerId = order.buyerId,
                status = order.status,
                totalAmount = order.totalAmount,
                items = orderItems.map { OrderItemResponse.from(it) },
                createdAt = order.createdAt
            )
        }
    }
}
