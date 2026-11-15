package com.hoppingmall.order.order.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.dto.response.SellerOrderResponse
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderQueryServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository
) : OrderQueryService {

    override fun getOrder(orderId: Long, userId: Long, isAdmin: Boolean): OrderResponse {
        val order = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException() 

        val orderItems = orderItemRepository.findByOrderId(orderId)
        if (order.buyerId != userId && !isAdmin && orderItems.none { it.sellerId == userId }) {
            throw OrderAccessDeniedException()
        }

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

    override fun getSellerOrders(sellerId: Long, status: OrderStatus?, pageable: Pageable): Slice<SellerOrderResponse> {
        val page = PageRequest.of(pageable.pageNumber, pageable.pageSize)
        val orderSlice = when {
            status == null -> orderRepository.findSellerOrders(sellerId, SELLER_HIDDEN_STATUSES, page)
            status in SELLER_HIDDEN_STATUSES -> return SliceImpl(emptyList(), page, false)
            else -> orderRepository.findSellerOrdersByStatus(sellerId, status, page)
        }
        val orderIds = orderSlice.content.mapNotNull { it.id }
        val sellerItems = if (orderIds.isEmpty()) emptyMap()
        else orderItemRepository.findByOrderIdInAndSellerId(orderIds, sellerId).groupBy { it.orderId }
        return orderSlice.map { order -> SellerOrderResponse.summary(order, sellerItems[order.id] ?: emptyList()) }
    }

    override fun getSellerOrder(orderId: Long, sellerId: Long): SellerOrderResponse {
        val order = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException()
        if (order.status in SELLER_HIDDEN_STATUSES) {
            throw OrderNotFoundException()
        }
        val sellerItems = orderItemRepository.findByOrderIdInAndSellerId(listOf(orderId), sellerId)
        if (sellerItems.isEmpty()) {
            throw OrderAccessDeniedException()
        }
        return SellerOrderResponse.detail(order, sellerItems)
    }

    companion object {
        private val SELLER_HIDDEN_STATUSES = setOf(OrderStatus.CREATED, OrderStatus.PAYING)
    }
}
