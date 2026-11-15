package com.hoppingmall.order.order.dto.response

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.enum.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class SellerOrderResponse(
    val id: Long,
    val status: OrderStatus,
    val sellerTotalAmount: BigDecimal,
    val items: List<OrderItemResponse>,
    val createdAt: LocalDateTime,
    val shippingAddress: OrderShippingAddressResponse? = null
) {
    companion object {
        private val SHIPPABLE_STATUSES = setOf(OrderStatus.PAID, OrderStatus.SHIPPED)

        fun summary(order: Order, sellerItems: List<OrderItem>): SellerOrderResponse = of(order, sellerItems, null)

        fun detail(order: Order, sellerItems: List<OrderItem>): SellerOrderResponse =
            of(
                order,
                sellerItems,
                order.shippingAddress
                    ?.takeIf { order.status in SHIPPABLE_STATUSES }
                    ?.let { OrderShippingAddressResponse.from(it) }
            )

        private fun of(order: Order, sellerItems: List<OrderItem>, shippingAddress: OrderShippingAddressResponse?) =
            SellerOrderResponse(
                id = order.id!!,
                status = order.status,
                sellerTotalAmount = sellerItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.totalPrice) },
                items = sellerItems.sortedBy { it.id }.map { OrderItemResponse.from(it) },
                createdAt = order.createdAt,
                shippingAddress = shippingAddress
            )
    }
}
