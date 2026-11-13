package com.hoppingmall.order.internal

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.hoppingmall.common.ApiResponse
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.enum.ShippingStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@RestController
@RequestMapping("/internal/api/v1")
class InternalOrderController(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val shippingRepository: ShippingRepository,
    private val refundItemRepository: RefundItemRepository
) {

    @GetMapping("/orders/{orderId}")
    fun getOrder(@PathVariable orderId: Long): ResponseEntity<ApiResponse<OrderSummaryResponse>> {
        val order = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException()
        return ResponseEntity.ok(ApiResponse.success(OrderSummaryResponse(
            orderId = order.id!!,
            buyerId = order.buyerId,
            totalAmount = order.totalAmount.setScale(2, RoundingMode.HALF_UP),
            status = order.status.name
        )))
    }

    @GetMapping("/orders/{orderId}/items")
    fun getOrderItems(@PathVariable orderId: Long): ResponseEntity<List<OrderItemResponse>> {
        val items = orderItemRepository.findByOrderId(orderId)
        return ResponseEntity.ok(items.map { item ->
            OrderItemResponse(
                id = item.id!!,
                orderId = item.orderId,
                sellerId = item.sellerId,
                productId = item.productId,
                productName = item.productName,
                productPrice = item.productPrice,
                quantity = item.quantity,
                totalPrice = item.totalPrice
            )
        })
    }

    @GetMapping("/order-items/{orderItemId}")
    fun getOrderItem(@PathVariable orderItemId: Long): ResponseEntity<OrderItemResponse> {
        val item = orderItemRepository.findByIdOrNull(orderItemId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(OrderItemResponse(
            id = item.id!!,
            orderId = item.orderId,
            sellerId = item.sellerId,
            productId = item.productId,
            productName = item.productName,
            productPrice = item.productPrice,
            quantity = item.quantity,
            totalPrice = item.totalPrice
        ))
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Transactional
    fun cancelOrder(@PathVariable orderId: Long): ResponseEntity<Void> {
        val order = orderRepository.findByIdOrNull(orderId)
            ?: return ResponseEntity.notFound().build()
        if (order.isCancelled() || order.isRefunded()) {
            return ResponseEntity.ok().build()
        }
        if (order.isRefundable()) {
            order.completeRefund()
        } else {
            order.updateStatus(com.hoppingmall.order.order.enum.OrderStatus.CANCELLED)
        }
        orderRepository.save(order)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/orders/{orderId}/delivered")
    fun isDelivered(@PathVariable orderId: Long, @RequestParam buyerId: Long): ResponseEntity<Boolean> {
        val order = orderRepository.findByIdOrNull(orderId)
            ?: return ResponseEntity.ok(false)
        if (order.buyerId != buyerId) return ResponseEntity.ok(false)
        val shipping = shippingRepository.findByOrderId(orderId)
        return ResponseEntity.ok(shipping?.status == ShippingStatus.DELIVERED)
    }

    @GetMapping("/order-items/delivered")
    fun getDeliveredOrderItems(
        @RequestParam sellerId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime
    ): ResponseEntity<List<OrderItemResponse>> {
        val items = orderItemRepository.findDeliveredItemsBySellerAndPeriod(sellerId, startDate, endDate)
        return ResponseEntity.ok(items.map { item ->
            OrderItemResponse(
                id = item.id!!,
                orderId = item.orderId,
                sellerId = item.sellerId,
                productId = item.productId,
                productName = item.productName,
                productPrice = item.productPrice,
                quantity = item.quantity,
                totalPrice = item.totalPrice
            )
        })
    }

    @GetMapping("/refunds/completed")
    fun getCompletedRefunds(
        @RequestParam sellerId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime
    ): ResponseEntity<List<RefundResponse>> {
        val refunds = refundItemRepository.findSettlementRefundsBySellerAndPeriod(sellerId, startDate, endDate)
        return ResponseEntity.ok(refunds.map { refund ->
            RefundResponse(
                id = refund.refundId,
                refundAmount = refund.saleAmount
            )
        })
    }

    data class OrderItemResponse(
        val id: Long,
        val orderId: Long,
        val sellerId: Long,
        val productId: Long,
        val productName: String,
        val productPrice: BigDecimal,
        val quantity: Int,
        val totalPrice: BigDecimal
    )

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
    data class OrderSummaryResponse(
        val orderId: Long,
        val buyerId: Long,
        val totalAmount: BigDecimal,
        val status: String
    )

    data class RefundResponse(
        val id: Long,
        val refundAmount: BigDecimal
    )
}
