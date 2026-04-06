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
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.order.exception.OrderPaymentCancellationFailedException
import com.hoppingmall.order.order.exception.OrderProductNotFoundException
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.TransactionalEventPublisherPort
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
    private val inventoryCommandPort: InventoryCommandPort,
    private val paymentCommandPort: PaymentCommandPort,
    private val transactionalEventPublisherPort: TransactionalEventPublisherPort,
    private val orderMetrics: OrderMetrics
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

        val items = cartItems.map { it.productId to it.quantity }
        val reservationMap = inventoryCommandPort.batchReserveStock(items)

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
        order.updateStatus(OrderStatus.PAYING)

        cartItemRepository.deleteAllById(request.cartItemIds)

        orderMetrics.recordOrderCreated(totalAmount)
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
            throw OrderInvalidStatusException()
        }

        val orderItems = orderItemRepository.findByOrderId(orderId)

        when (order.status) {
            OrderStatus.PAYING -> {
                order.updateStatus(OrderStatus.CANCELLED)
                if (!paymentCommandPort.cancelPayment(orderId)) {
                    throw OrderPaymentCancellationFailedException()
                }
                restoreInventory(orderItems)
            }
            OrderStatus.CREATED -> {
                order.updateStatus(OrderStatus.CANCELLED)
                restoreInventory(orderItems)
            }
            OrderStatus.PAID -> {
                order.updateStatus(OrderStatus.CANCEL_REQUESTED)
                transactionalEventPublisherPort.publishEvent(
                    aggregateType = "Order",
                    aggregateId = orderId.toString(),
                    eventType = "PaymentCancellationRequested",
                    eventData = mapOf(
                        "eventType" to "PaymentCancellationRequested",
                        "eventId" to "cancel-$orderId-${System.currentTimeMillis()}",
                        "orderId" to orderId
                    ),
                    topic = KafkaTopics.PAYMENT_COMPENSATION,
                    partitionKey = orderId.toString()
                )
            }
            else -> throw OrderInvalidStatusException()
        }

        orderMetrics.recordOrderCancelled()
        log.info("주문 취소: orderId={}, buyerId={}, status={}", orderId, buyerId, order.status)
        return OrderResponse.from(order, orderItems)
    }

    private fun restoreInventory(orderItems: List<OrderItem>) {
        val reservationIds = orderItems.mapNotNull { it.reservationId }
        if (reservationIds.isNotEmpty()) {
            inventoryCommandPort.cancelReservations(reservationIds)
        } else {
            orderItems.forEach { orderItem ->
                inventoryCommandPort.increaseStock(orderItem.productId, orderItem.quantity)
            }
        }
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
