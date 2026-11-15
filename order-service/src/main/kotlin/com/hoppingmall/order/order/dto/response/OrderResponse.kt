package com.hoppingmall.order.order.dto.response

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.enum.OrderStatus
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

data class OrderResponse(
    val id: Long,
    val buyerId: Long,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val items: List<OrderItemResponse>,
    val createdAt: LocalDateTime,
    val shippingAddress: OrderShippingAddressResponse? = null,
    val autoCancelAt: OffsetDateTime? = null,
    val autoCancelInSeconds: Long? = null
) {
    companion object {
        fun from(order: Order, orderItems: List<OrderItem>, payingTimeoutMinutes: Long? = null): OrderResponse {
            val autoCancelAt = payingTimeoutMinutes?.let { order.autoCancelAt(it) }
            return OrderResponse(
                id = order.id!!,
                buyerId = order.buyerId,
                status = order.status,
                totalAmount = order.totalAmount,
                items = orderItems.map { OrderItemResponse.from(it) },
                createdAt = order.createdAt,
                shippingAddress = order.shippingAddress?.let { OrderShippingAddressResponse.from(it) },
                autoCancelAt = autoCancelAt?.atZone(ZoneId.systemDefault())?.toOffsetDateTime(),
                autoCancelInSeconds = autoCancelAt?.let { Duration.between(LocalDateTime.now(), it).seconds.coerceAtLeast(0) }
            )
        }
    }
}
