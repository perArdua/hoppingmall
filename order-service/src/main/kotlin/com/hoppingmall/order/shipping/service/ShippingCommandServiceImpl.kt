package com.hoppingmall.order.shipping.service

import org.springframework.data.repository.findByIdOrNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.common.NotificationType
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.order.exception.OrderShippingAddressRequiredException
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.dto.response.ShippingResponse
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.exception.ShippingAccessDeniedException
import com.hoppingmall.order.shipping.exception.ShippingAlreadyExistsException
import com.hoppingmall.order.shipping.exception.ShippingNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
class ShippingCommandServiceImpl(
    private val shippingRepository: ShippingRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val objectMapper: ObjectMapper
) : ShippingCommandService {

    override fun createShipping(sellerId: Long, request: ShippingCreateRequest, isAdmin: Boolean): ShippingResponse {
        orderRepository.lockById(request.orderId)
        val order = orderRepository.findByIdOrNull(request.orderId) ?: throw OrderNotFoundException()

        val orderItems = orderItemRepository.findByOrderId(request.orderId)
        if (orderItems.isEmpty()) {
            throw OrderNotFoundException()
        }
        if (!isAdmin && orderItems.none { it.sellerId == sellerId }) {
            throw ShippingAccessDeniedException()
        }

        if (order.status != OrderStatus.PAID) {
            throw OrderInvalidStatusException()
        }

        val recipient = recipientOf(order, request)

        shippingRepository.findByOrderId(request.orderId)?.let { existing ->
            if (!existing.canRestart()) {
                throw ShippingAlreadyExistsException()
            }
            existing.restart(request.carrierName, request.trackingNumber, recipient.name, recipient.phone, recipient.address)
            return ShippingResponse.from(existing)
        }

        val shipping = Shipping.create(
            orderId = request.orderId,
            buyerId = order.buyerId,
            carrierName = request.carrierName,
            trackingNumber = request.trackingNumber,
            recipientName = recipient.name,
            recipientPhone = recipient.phone,
            recipientAddress = recipient.address
        )

        val savedShipping = shippingRepository.save(shipping)
        return ShippingResponse.from(savedShipping)
    }

    override fun updateShippingStatus(
        sellerId: Long,
        shippingId: Long,
        request: ShippingStatusUpdateRequest,
        isAdmin: Boolean
    ): ShippingResponse {
        shippingRepository.findOrderIdById(shippingId)?.let {
            orderRepository.lockById(it)
            shippingRepository.lockOrderIdById(shippingId)
        }
        val shipping = shippingRepository.findByIdOrNull(shippingId) ?: throw ShippingNotFoundException()

        val order = orderRepository.findByIdOrNull(shipping.orderId) ?: throw OrderNotFoundException()

        if (!isAdmin && !orderItemRepository.existsByOrderIdAndSellerId(shipping.orderId, sellerId)) {
            throw ShippingAccessDeniedException()
        }

        moveShipping(shipping, order, request.status)

        return ShippingResponse.from(shipping)
    }

    override fun updateStatusByOrder(userId: Long, orderId: Long, status: OrderStatus, isAdmin: Boolean): OrderResponse {
        orderRepository.lockById(orderId)
        val order = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException()
        if (!isAdmin && !orderItemRepository.existsByOrderIdAndSellerId(orderId, userId)) {
            throw OrderAccessDeniedException()
        }
        val target = when (status) {
            OrderStatus.SHIPPED -> ShippingStatus.IN_TRANSIT
            OrderStatus.DELIVERED -> ShippingStatus.DELIVERED
            else -> throw OrderInvalidStatusException()
        }
        val shipping = shippingRepository.findByOrderId(orderId) ?: throw ShippingNotFoundException()
        shipping.id?.let { shippingRepository.lockOrderIdById(it) }

        moveShipping(shipping, order, target)

        return OrderResponse.from(order, orderItemRepository.findByOrderId(orderId))
    }

    private fun moveShipping(shipping: Shipping, order: Order, status: ShippingStatus) {
        shipping.updateStatus(status)

        when (status) {
            ShippingStatus.IN_TRANSIT -> {
                if (orderRepository.isHeldBySagaTimeout(shipping.orderId)) {
                    throw OrderInvalidStatusException()
                }
                order.updateStatus(OrderStatus.SHIPPED)
                publishShippingNotification(
                    shipping = shipping,
                    notificationType = NotificationType.SHIPPING_STARTED,
                    title = "상품이 배송 시작되었습니다",
                    content = "상품이 배송 중입니다. 운송장번호: ${shipping.trackingNumber}"
                )
            }
            ShippingStatus.DELIVERED -> {
                order.updateStatus(OrderStatus.DELIVERED)
                publishShippingNotification(
                    shipping = shipping,
                    notificationType = NotificationType.SHIPPING_DELIVERED,
                    title = "배송이 완료되었습니다",
                    content = "배송이 완료되었습니다."
                )
            }
            ShippingStatus.FAILED -> order.updateStatus(OrderStatus.PAID)
            else -> {}
        }
    }

    private fun publishShippingNotification(
        shipping: Shipping,
        notificationType: NotificationType,
        title: String,
        content: String
    ) {
        val attemptSuffix = if (shipping.attempt > 1) "-${shipping.attempt}" else ""
        val eventId = "shipping-${notificationType.name.lowercase()}-${shipping.id}$attemptSuffix"
        val metadata = objectMapper.writeValueAsString(
            mapOf(
                "orderId" to shipping.orderId,
                "shippingId" to shipping.id!!,
                "trackingNumber" to shipping.trackingNumber,
                "carrierName" to shipping.carrierName
            )
        )

        transactionalEventPublisher.publishEvent(
            aggregateType = "Shipping",
            aggregateId = shipping.id!!.toString(),
            eventType = "${notificationType.name}NotificationRequested",
            eventData = mapOf(
                "eventId" to eventId,
                "userId" to shipping.buyerId,
                "type" to notificationType.toString(),
                "title" to title,
                "content" to content,
                "metadata" to metadata
            ),
            topic = KafkaTopics.NOTIFICATION,
            partitionKey = shipping.buyerId.toString()
        )
    }

    private fun recipientOf(order: Order, request: ShippingCreateRequest): Recipient {
        order.shippingAddress?.let {
            return Recipient(it.recipientName, it.recipientPhone, it.toRecipientAddress())
        }
        val name = request.recipientName?.trim()?.takeIf { it.isNotEmpty() }
        val phone = request.recipientPhone?.trim()?.takeIf { it.isNotEmpty() }
        val address = request.recipientAddress?.trim()?.takeIf { it.isNotEmpty() }
        if (name == null || phone == null || address == null) {
            throw OrderShippingAddressRequiredException()
        }
        return Recipient(name, phone, address)
    }

    private class Recipient(val name: String, val phone: String, val address: String)
}
