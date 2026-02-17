package com.hoppingmall.mall.shipping.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import com.hoppingmall.mall.shipping.domain.Shipping
import com.hoppingmall.mall.shipping.domain.repository.ShippingRepository
import com.hoppingmall.mall.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.mall.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.mall.shipping.dto.response.ShippingResponse
import com.hoppingmall.mall.shipping.enum.ShippingStatus
import com.hoppingmall.mall.shipping.exception.ShippingAlreadyExistsException
import com.hoppingmall.mall.shipping.exception.ShippingNotFoundException
import com.hoppingmall.mall.order.exception.OrderInvalidStatusException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ShippingCommandServiceImpl(
    private val shippingRepository: ShippingRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val objectMapper: ObjectMapper
) : ShippingCommandService {

    override fun createShipping(sellerId: Long, request: ShippingCreateRequest): ShippingResponse {
        val order = orderRepository.findById(request.orderId)
            .orElseThrow { OrderNotFoundException() }

        if (order.status != OrderStatus.PAID) {
            throw OrderInvalidStatusException()
        }

        if (shippingRepository.findByOrderId(request.orderId) != null) {
            throw ShippingAlreadyExistsException()
        }

        val orderItems = orderItemRepository.findByOrderId(request.orderId)
        val hasSellerProduct = orderItems.any { true }
        if (!hasSellerProduct) {
            throw OrderNotFoundException()
        }

        val shipping = Shipping.create(
            orderId = request.orderId,
            buyerId = order.buyerId,
            carrierName = request.carrierName,
            trackingNumber = request.trackingNumber,
            recipientName = request.recipientName,
            recipientPhone = request.recipientPhone,
            recipientAddress = request.recipientAddress
        )

        val savedShipping = shippingRepository.save(shipping)
        return ShippingResponse.from(savedShipping)
    }

    override fun updateShippingStatus(
        sellerId: Long,
        shippingId: Long,
        request: ShippingStatusUpdateRequest
    ): ShippingResponse {
        val shipping = shippingRepository.findById(shippingId)
            .orElseThrow { ShippingNotFoundException() }

        shipping.updateStatus(request.status)

        val order = orderRepository.findById(shipping.orderId)
            .orElseThrow { OrderNotFoundException() }

        when (request.status) {
            ShippingStatus.IN_TRANSIT -> {
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
            else -> {}
        }

        return ShippingResponse.from(shipping)
    }

    private fun publishShippingNotification(
        shipping: Shipping,
        notificationType: NotificationType,
        title: String,
        content: String
    ) {
        val eventId = "shipping-${notificationType.name.lowercase()}-${shipping.id}"
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
            topic = "notification",
            partitionKey = shipping.buyerId.toString()
        )
    }
}
