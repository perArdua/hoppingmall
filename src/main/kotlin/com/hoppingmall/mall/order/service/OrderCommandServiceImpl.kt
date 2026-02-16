package com.hoppingmall.mall.order.service

import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.dto.request.OrderCreateRequest
import com.hoppingmall.mall.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.mall.order.dto.response.OrderResponse
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderAccessDeniedException
import com.hoppingmall.mall.order.exception.OrderEmptyItemsException
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class OrderCommandServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val cartItemRepository: CartItemRepository,
    private val inventoryCommandService: InventoryCommandService
) : OrderCommandService {

    override fun createOrder(buyerId: Long, request: OrderCreateRequest): OrderResponse {
        val cartItems = cartItemRepository.findAllById(request.cartItemIds)

        if (cartItems.isEmpty()) {
            throw OrderEmptyItemsException()
        }

        val hasOtherBuyerItem = cartItems.any { it.buyerId != buyerId }
        if (hasOtherBuyerItem) {
            throw OrderAccessDeniedException()
        }

        cartItems.sortedBy { it.productId }.forEach { cartItem ->
            inventoryCommandService.decreaseStock(cartItem.productId, cartItem.quantity)
        }

        val totalAmount = cartItems.sumOf { BigDecimal(it.productPrice) * BigDecimal(it.quantity) }

        val order = orderRepository.save(Order.create(buyerId = buyerId, totalAmount = totalAmount))

        val orderItems = cartItems.map { cartItem ->
            OrderItem.create(
                orderId = order.id!!,
                productId = cartItem.productId,
                productName = cartItem.productName,
                productPrice = BigDecimal(cartItem.productPrice),
                quantity = cartItem.quantity
            )
        }
        val savedOrderItems = orderItemRepository.saveAll(orderItems)

        cartItemRepository.deleteAllById(request.cartItemIds)

        return OrderResponse.from(order, savedOrderItems)
    }

    override fun cancelOrder(buyerId: Long, orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException() }

        if (order.buyerId != buyerId) {
            throw OrderAccessDeniedException()
        }

        order.updateStatus(OrderStatus.CANCELLED)

        val orderItems = orderItemRepository.findByOrderId(orderId)
        orderItems.forEach { orderItem ->
            inventoryCommandService.increaseStock(orderItem.productId, orderItem.quantity)
        }

        return OrderResponse.from(order, orderItems)
    }

    override fun updateOrderStatus(orderId: Long, request: OrderStatusUpdateRequest): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException() }

        order.updateStatus(request.status)

        val orderItems = orderItemRepository.findByOrderId(orderId)
        return OrderResponse.from(order, orderItems)
    }
}
