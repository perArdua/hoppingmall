package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderEmptyItemsException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.order.exception.OrderProductNotFoundException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class OrderCommandServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val cartItemRepository: CartItemRepository,
    private val productQueryPort: ProductQueryPort,
    private val inventoryCommandPort: InventoryCommandPort
) : OrderCommandService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun createOrder(buyerId: Long, request: OrderCreateRequest): OrderResponse {
        val cartItems = cartItemRepository.findAllById(request.cartItemIds)

        if (cartItems.isEmpty()) {
            throw OrderEmptyItemsException()
        }

        val hasOtherBuyerItem = cartItems.any { it.buyerId != buyerId }
        if (hasOtherBuyerItem) {
            throw OrderAccessDeniedException()
        }

        val productIds = cartItems.map { it.productId }.distinct()
        val productMap = productQueryPort.findProductsByIds(productIds).associateBy { it.id }
        productIds.forEach { productId ->
            if (!productMap.containsKey(productId)) throw OrderProductNotFoundException()
        }

        val reservationMap = mutableMapOf<Long, String>()
        try {
            cartItems.sortedBy { it.productId }.forEach { cartItem ->
                val reservationId = inventoryCommandPort.reserveStock(cartItem.productId, cartItem.quantity)
                reservationMap[cartItem.productId] = reservationId
            }
        } catch (e: Exception) {
            if (reservationMap.isNotEmpty()) {
                inventoryCommandPort.cancelReservations(reservationMap.values.toList())
            }
            throw e
        }

        val totalAmount = cartItems.fold(BigDecimal.ZERO) { acc, it -> acc.add(it.productPrice.multiply(BigDecimal(it.quantity))) }

        val order = orderRepository.save(Order.create(buyerId = buyerId, totalAmount = totalAmount))

        val orderItems = cartItems.map { cartItem ->
            OrderItem.create(
                orderId = order.id!!,
                sellerId = productMap[cartItem.productId]!!.sellerId,
                productId = cartItem.productId,
                productName = cartItem.productName,
                productPrice = cartItem.productPrice,
                quantity = cartItem.quantity,
                reservationId = reservationMap[cartItem.productId]
            )
        }
        val savedOrderItems = orderItemRepository.saveAll(orderItems)

        cartItemRepository.deleteAllById(request.cartItemIds)

        log.info("주문 생성: orderId={}, buyerId={}, totalAmount={}, itemCount={}", order.id, buyerId, totalAmount, orderItems.size)
        return OrderResponse.from(order, savedOrderItems)
    }

    override fun cancelOrder(buyerId: Long, orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException() }

        if (order.buyerId != buyerId) {
            throw OrderAccessDeniedException()
        }

        if (!order.isCancellable()) {
            throw com.hoppingmall.order.order.exception.OrderInvalidStatusException()
        }

        order.updateStatus(OrderStatus.CANCELLED)

        val orderItems = orderItemRepository.findByOrderId(orderId)
        val reservationIds = orderItems.mapNotNull { it.reservationId }
        if (reservationIds.isNotEmpty()) {
            inventoryCommandPort.cancelReservations(reservationIds)
        } else {
            orderItems.forEach { orderItem ->
                inventoryCommandPort.increaseStock(orderItem.productId, orderItem.quantity)
            }
        }

        log.info("주문 취소: orderId={}, buyerId={}", orderId, buyerId)
        return OrderResponse.from(order, orderItems)
    }

    override fun updateOrderStatus(orderId: Long, request: OrderStatusUpdateRequest, userId: Long, isAdmin: Boolean): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException() }

        if (order.buyerId != userId && !isAdmin) {
            throw OrderAccessDeniedException()
        }

        order.updateStatus(request.status)

        val orderItems = orderItemRepository.findByOrderId(orderId)
        return OrderResponse.from(order, orderItems)
    }
}
