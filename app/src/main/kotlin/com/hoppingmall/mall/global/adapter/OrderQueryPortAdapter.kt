package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.order.api.OrderItemInfo
import com.hoppingmall.mall.order.api.OrderQueryPort
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class OrderQueryPortAdapter(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository
) : OrderQueryPort {

    override fun isDelivered(orderId: Long, buyerId: Long): Boolean {
        val order = orderRepository.findByIdOrNull(orderId) ?: return false
        return order.buyerId == buyerId && order.status == OrderStatus.DELIVERED
    }

    override fun findOrderItemById(orderItemId: Long): OrderItemInfo? {
        return orderItemRepository.findByIdOrNull(orderItemId)?.toInfo()
    }

    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        return orderItemRepository.findByOrderId(orderId).map { it.toInfo() }
    }

    private fun OrderItem.toInfo() = OrderItemInfo(
        id = id!!,
        orderId = orderId,
        sellerId = sellerId,
        productId = productId,
        productName = productName,
        productPrice = productPrice,
        quantity = quantity,
        totalPrice = totalPrice
    )
}
