package com.hoppingmall.order.grpc

import io.grpc.Status
import io.grpc.StatusException
import net.devh.boot.grpc.server.service.GrpcService
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.enum.ShippingStatus

@GrpcService
class GrpcOrderQueryService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val shippingRepository: ShippingRepository
) : OrderQueryServiceGrpcKt.OrderQueryServiceCoroutineImplBase() {

    override suspend fun findOrderItemsByOrderId(request: OrderIdRequest): OrderItemListResponse {
        val orderItems = orderItemRepository.findByOrderId(request.orderId)
        return orderItemListResponse {
            items += orderItems.map { item ->
                orderItemResponse {
                    id = item.id!!
                    orderId = item.orderId
                    sellerId = item.sellerId
                    productId = item.productId
                    productName = item.productName
                    productPrice = item.productPrice.toPlainString()
                    quantity = item.quantity
                    totalPrice = item.totalPrice.toPlainString()
                }
            }
        }
    }

    override suspend fun isDelivered(request: DeliveredCheckRequest): DeliveredResponse {
        val order = orderRepository.findById(request.orderId).orElse(null)
        if (order == null || order.buyerId != request.buyerId) {
            return deliveredResponse { delivered = false }
        }
        val shipping = shippingRepository.findByOrderId(request.orderId)
        return deliveredResponse { delivered = shipping?.status == ShippingStatus.DELIVERED }
    }

    override suspend fun findOrderItemById(request: OrderItemIdRequest): OrderItemResponse {
        val item = orderItemRepository.findById(request.orderItemId).orElse(null)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("OrderItem not found: ${request.orderItemId}"))
        return orderItemResponse {
            id = item.id!!
            orderId = item.orderId
            sellerId = item.sellerId
            productId = item.productId
            productName = item.productName
            productPrice = item.productPrice.toPlainString()
            quantity = item.quantity
            totalPrice = item.totalPrice.toPlainString()
        }
    }
}
