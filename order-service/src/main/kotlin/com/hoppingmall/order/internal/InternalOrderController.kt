package com.hoppingmall.order.internal

import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.enum.ShippingStatus
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/internal/api/v1")
class InternalOrderController(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val shippingRepository: ShippingRepository,
    private val refundRepository: RefundRepository
) {

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
        val item = orderItemRepository.findById(orderItemId).orElse(null)
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
        val order = orderRepository.findById(orderId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (order.isCancelled()) {
            return ResponseEntity.ok().build()
        }
        order.updateStatus(com.hoppingmall.order.order.enum.OrderStatus.CANCELLED)
        orderRepository.save(order)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/orders/{orderId}/delivered")
    fun isDelivered(@PathVariable orderId: Long, @RequestParam buyerId: Long): ResponseEntity<Boolean> {
        val order = orderRepository.findById(orderId).orElse(null)
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
        val refunds = refundRepository.findBySellerIdAndStatusAndCompletedAtBetween(
            sellerId, RefundStatus.COMPLETED, startDate, endDate
        )
        return ResponseEntity.ok(refunds.map { refund ->
            RefundResponse(
                id = refund.id!!,
                refundAmount = refund.refundAmount
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

    data class RefundResponse(
        val id: Long,
        val refundAmount: BigDecimal
    )
}
