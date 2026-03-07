package com.hoppingmall.mall.order.service

import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.dto.response.OrderResponse
import com.hoppingmall.mall.order.exception.OrderAccessDeniedException
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    override fun getMyOrders(buyerId: Long, pageable: Pageable): Page<OrderResponse> {
        val orderPage = orderRepository.findByBuyerId(buyerId, pageable)
        val orderIds = orderPage.content.mapNotNull { it.id }
        val orderItemMap = orderItemRepository.findByOrderIdIn(orderIds)
            .groupBy { it.orderId }

        return orderPage.map { order ->
            OrderResponse.from(order, orderItemMap[order.id] ?: emptyList())
        }
    }
}
