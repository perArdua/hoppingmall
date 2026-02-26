package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.exception.PaymentNotFoundException
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.refund.domain.Refund
import com.hoppingmall.mall.refund.domain.RefundItem
import com.hoppingmall.mall.refund.domain.repository.RefundItemRepository
import com.hoppingmall.mall.refund.domain.repository.RefundRepository
import com.hoppingmall.mall.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.mall.refund.dto.event.RefundItemEvent
import com.hoppingmall.mall.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.mall.refund.dto.request.RefundCreateRequest
import com.hoppingmall.mall.refund.dto.response.RefundResponse
import com.hoppingmall.mall.refund.enum.RefundStatus
import com.hoppingmall.mall.refund.exception.RefundAccessDeniedException
import com.hoppingmall.mall.refund.exception.RefundAlreadyExistsException
import com.hoppingmall.mall.refund.exception.RefundNotFoundException
import com.hoppingmall.mall.shipping.domain.repository.ShippingRepository
import com.hoppingmall.mall.shipping.enum.ShippingStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional
class RefundCommandServiceImpl(
    private val refundRepository: RefundRepository,
    private val refundItemRepository: RefundItemRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val paymentRepository: PaymentRepository,
    private val productRepository: ProductRepository,
    private val shippingRepository: ShippingRepository,
    private val refundEventPublisher: RefundEventPublisher
) : RefundCommandService {

    private val refundableOrderStatuses = setOf(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)

    override fun requestRefund(buyerId: Long, request: RefundCreateRequest): RefundResponse {
        val order = orderRepository.findById(request.orderId)
            .orElseThrow { OrderNotFoundException() }

        if (order.buyerId != buyerId) {
            throw RefundAccessDeniedException()
        }

        if (order.status !in refundableOrderStatuses) {
            throw com.hoppingmall.mall.refund.exception.RefundException(
                com.hoppingmall.mall.refund.exception.code.RefundErrorCode.REFUND_INVALID_ORDER_STATUS
            )
        }

        val payment = paymentRepository.findByOrderId(request.orderId)
            ?: throw PaymentNotFoundException()

        if (!payment.isSuccess()) {
            throw com.hoppingmall.mall.refund.exception.RefundException(
                com.hoppingmall.mall.refund.exception.code.RefundErrorCode.REFUND_INVALID_PAYMENT_STATUS
            )
        }

        val existingRefunds = refundRepository.findByOrderIdAndStatusNot(request.orderId, RefundStatus.REJECTED)
        if (existingRefunds.isNotEmpty()) {
            throw RefundAlreadyExistsException()
        }

        val orderItems = orderItemRepository.findByOrderId(request.orderId)
        val orderItemMap = orderItems.associateBy { it.id!! }

        request.items.forEach { item ->
            val orderItem = orderItemMap[item.orderItemId]
                ?: throw com.hoppingmall.mall.refund.exception.RefundException(
                    com.hoppingmall.mall.refund.exception.code.RefundErrorCode.REFUND_INVALID_ITEM
                )
            if (item.quantity > orderItem.quantity) {
                throw com.hoppingmall.mall.refund.exception.RefundException(
                    com.hoppingmall.mall.refund.exception.code.RefundErrorCode.REFUND_INVALID_ITEM
                )
            }
        }

        val refundAmount = request.items.sumOf { item ->
            val orderItem = orderItemMap[item.orderItemId]!!
            orderItem.productPrice.multiply(BigDecimal(item.quantity))
        }

        val isFullRefund = request.items.size == orderItems.size &&
            request.items.all { item ->
                val orderItem = orderItemMap[item.orderItemId]!!
                item.quantity == orderItem.quantity
            }

        val firstOrderItem = orderItemMap[request.items.first().orderItemId]!!
        val product = productRepository.findById(firstOrderItem.productId).orElse(null)
        val sellerId = product?.sellerId ?: 0L

        val refund = Refund.create(
            orderId = request.orderId,
            paymentId = payment.id!!,
            buyerId = buyerId,
            sellerId = sellerId,
            reason = request.reason,
            reasonDetail = request.reasonDetail,
            refundAmount = refundAmount,
            isFullRefund = isFullRefund
        )
        val savedRefund = refundRepository.save(refund)

        val refundItems = request.items.map { item ->
            val orderItem = orderItemMap[item.orderItemId]!!
            RefundItem.create(
                refundId = savedRefund.id!!,
                orderItemId = item.orderItemId,
                productId = orderItem.productId,
                productName = orderItem.productName,
                productPrice = orderItem.productPrice,
                quantity = item.quantity
            )
        }
        val savedRefundItems = refundItemRepository.saveAll(refundItems)

        val shipping = shippingRepository.findByOrderId(request.orderId)
        val isAutoApprove = shipping == null || shipping.status == ShippingStatus.PREPARING

        if (isAutoApprove) {
            savedRefund.approve(buyerId)
            publishRefundCompletedEvent(savedRefund, savedRefundItems, payment)
            savedRefund.complete()
            refundRepository.save(savedRefund)
        }

        return RefundResponse.from(savedRefund, savedRefundItems)
    }

    override fun approveRefund(refundId: Long, approverId: Long): RefundResponse {
        val refund = refundRepository.findById(refundId)
            .orElseThrow { RefundNotFoundException() }

        if (refund.sellerId != approverId) {
            throw RefundAccessDeniedException()
        }

        refund.approve(approverId)

        val refundItems = refundItemRepository.findByRefundId(refundId)
        val payment = paymentRepository.findById(refund.paymentId)
            .orElseThrow { PaymentNotFoundException() }

        publishRefundCompletedEvent(refund, refundItems, payment)
        refund.complete()
        refundRepository.save(refund)

        return RefundResponse.from(refund, refundItems)
    }

    override fun rejectRefund(refundId: Long, approverId: Long, request: RefundApprovalRequest): RefundResponse {
        val refund = refundRepository.findById(refundId)
            .orElseThrow { RefundNotFoundException() }

        if (refund.sellerId != approverId) {
            throw RefundAccessDeniedException()
        }

        refund.reject(request.rejectionReason ?: "", approverId)
        refundRepository.save(refund)

        val refundItems = refundItemRepository.findByRefundId(refundId)
        return RefundResponse.from(refund, refundItems)
    }

    private fun publishRefundCompletedEvent(
        refund: Refund,
        refundItems: List<RefundItem>,
        payment: Payment
    ) {
        val pointRefundAmount = if (refund.isFullRefund) {
            payment.pointAmount
        } else {
            if (payment.amount > BigDecimal.ZERO) {
                payment.pointAmount.multiply(refund.refundAmount)
                    .divide(payment.amount, 0, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
        }

        refundEventPublisher.publishRefundCompletedEvent(
            RefundCompletedEvent(
                refundId = refund.id!!,
                orderId = refund.orderId,
                paymentId = payment.id!!,
                buyerId = refund.buyerId,
                refundAmount = refund.refundAmount,
                pointRefundAmount = pointRefundAmount,
                isFullRefund = refund.isFullRefund,
                couponId = payment.couponId,
                items = refundItems.map { item ->
                    RefundItemEvent(
                        productId = item.productId,
                        quantity = item.quantity,
                        refundPrice = item.refundPrice
                    )
                }
            )
        )
    }
}
