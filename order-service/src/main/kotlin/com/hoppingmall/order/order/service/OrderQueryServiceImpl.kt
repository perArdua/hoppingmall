package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderQueryServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository
) : OrderQueryService {

    override fun getOrder(orderId: Long, buyerId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException() }

        if (order.buyerId != buyerId) {
            throw OrderAccessDeniedException()
        }

        val orderItems = orderItemRepository.findByOrderId(orderId)
        return OrderResponse.from(order, orderItems)
    }

    override fun getMyOrders(buyerId: Long, pageable: Pageable): Slice<OrderResponse> {
        val orderSlice = orderRepository.findByBuyerId(buyerId, pageable)
        val orderIds = orderSlice.content.mapNotNull { it.id }
        val orderItemMap = orderItemRepository.findByOrderIdIn(orderIds)
            .groupBy { it.orderId }

        return orderSlice.map { order ->
            OrderResponse.from(order, orderItemMap[order.id] ?: emptyList())
        }
    }
}
