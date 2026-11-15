package com.hoppingmall.order.refund.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.refund.config.RefundPolicyProperties
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundAmountCalculator
import com.hoppingmall.order.refund.domain.RefundDeadline
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.RefundPointShare
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.order.refund.dto.event.RefundItemEvent
import com.hoppingmall.order.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.refund.exception.RefundAccessDeniedException
import com.hoppingmall.order.refund.exception.RefundAlreadyExistsException
import com.hoppingmall.order.refund.exception.RefundException
import com.hoppingmall.order.refund.exception.RefundNotFoundException
import com.hoppingmall.order.refund.exception.RefundPaymentNotFoundException
import com.hoppingmall.order.refund.exception.code.RefundErrorCode
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.enum.ShippingStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class RefundCommandServiceImpl(
    private val refundRepository: RefundRepository,
    private val refundItemRepository: RefundItemRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val paymentQueryPort: PaymentQueryPort,
    private val shippingRepository: ShippingRepository,
    private val refundEventPublisher: RefundEventPublisher,
    private val transactionTemplate: TransactionTemplate
) : RefundCommandService {

    private val refundableOrderStatuses = setOf(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)

    private val shippedStatuses = setOf(ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED)

    @Autowired(required = false)
    private var refundPolicy: RefundPolicyProperties = RefundPolicyProperties()

    override fun requestRefund(buyerId: Long, request: RefundCreateRequest): RefundResponse {
        if (!request.reason.acceptsNewRequest) {
            throw RefundException(RefundErrorCode.REFUND_REASON_NOT_ACCEPTED)
        }

        if (request.items.distinctBy { it.orderItemId }.size != request.items.size) {
            throw RefundException(RefundErrorCode.REFUND_DUPLICATE_ITEM)
        }

        val payment = paymentQueryPort.findByOrderId(request.orderId)
            ?: throw RefundPaymentNotFoundException()

        if (!payment.isSuccess()) {
            throw RefundException(RefundErrorCode.REFUND_INVALID_PAYMENT_STATUS)
        }

        return transactionTemplate.execute { _ ->
            orderRepository.lockById(request.orderId)
            val order = orderRepository.findByIdOrNull(request.orderId) ?: throw OrderNotFoundException() 

            if (order.buyerId != buyerId) {
                throw RefundAccessDeniedException()
            }

            if (order.status !in refundableOrderStatuses) {
                throw RefundException(RefundErrorCode.REFUND_INVALID_ORDER_STATUS)
            }

            val shipping = shippingRepository.findByOrderId(request.orderId)
            val receivedAt = RefundDeadline.receivedAt(order, shipping)
            if (receivedAt != null && RefundDeadline(refundPolicy).isClosed(request.reason, receivedAt, LocalDateTime.now())) {
                throw RefundException(
                    if (request.reason.isSellerFault) RefundErrorCode.REFUND_SELLER_FAULT_WINDOW_EXPIRED
                    else RefundErrorCode.REFUND_WINDOW_EXPIRED
                )
            }

            val orderItems = orderItemRepository.findByOrderId(request.orderId)
            val orderItemMap = orderItems.associateBy { it.id!! }

            val inProgressItemIds = refundItemRepository.findRefundQuantitiesByStatus(request.orderId)
                .filter { it.status in IN_PROGRESS_STATUSES && it.quantity > 0 }
                .map { it.orderItemId }
                .toSet()
            if (request.items.any { it.orderItemId in inProgressItemIds }) {
                throw RefundAlreadyExistsException()
            }

            if (request.reason == RefundReason.CHANGE_OF_MIND &&
                request.items.any { orderItemMap[it.orderItemId]?.withdrawalRestricted == true }
            ) {
                throw RefundException(RefundErrorCode.REFUND_WITHDRAWAL_RESTRICTED)
            }

            val refundedQuantities = refundItemRepository.findRefundedQuantitiesByOrderId(request.orderId)
            val refundedMap = refundedQuantities.associate { it.orderItemId to it.totalRefundedQuantity.toInt() }
            val refundedAmountMap = refundedQuantities.associate { it.orderItemId to it.totalRefundedAmount }

            request.items.forEach { item ->
                val orderItem = orderItemMap[item.orderItemId]
                    ?: throw RefundException(RefundErrorCode.REFUND_INVALID_ITEM)
                val alreadyRefunded = refundedMap[item.orderItemId] ?: 0
                val availableQuantity = orderItem.quantity - alreadyRefunded
                if (item.quantity > availableQuantity) {
                    throw RefundException(RefundErrorCode.REFUND_QUANTITY_EXCEEDED)
                }
            }

            val refundAmountCalculator = RefundAmountCalculator(orderItems, order.totalAmount.subtract(payment.amount))
            val refundPrices = request.items.map { item ->
                refundAmountCalculator.refundPrice(
                    orderItemId = item.orderItemId,
                    quantity = item.quantity,
                    alreadyRefundedQuantity = refundedMap[item.orderItemId] ?: 0,
                    alreadyRefundedAmount = refundedAmountMap[item.orderItemId] ?: BigDecimal.ZERO
                )
            }
            val productAmount = refundPrices.fold(BigDecimal.ZERO, BigDecimal::add)
            val returnShippingFee = if (!request.reason.isSellerFault && shipping?.status in shippedStatuses) {
                refundPolicy.returnShippingFee
            } else {
                BigDecimal.ZERO
            }
            if (returnShippingFee > productAmount) {
                throw RefundException(RefundErrorCode.REFUND_RETURN_SHIPPING_FEE_EXCEEDED)
            }
            val refundAmount = productAmount.subtract(returnShippingFee)

            val requestItemMap = request.items.associate { it.orderItemId to it.quantity }
            val isFullRefund = orderItems.all { orderItem ->
                val alreadyRefunded = refundedMap[orderItem.id!!] ?: 0
                val currentRefund = requestItemMap[orderItem.id!!] ?: 0
                alreadyRefunded + currentRefund == orderItem.quantity
            }

            val pointRefundAmount = RefundPointShare.of(
                usedPoints = payment.pointAmount,
                orderTotal = order.totalAmount,
                refundedGross = request.items.fold(BigDecimal.ZERO) { acc, item ->
                    acc.add(orderItemMap.getValue(item.orderItemId).productPrice.multiply(BigDecimal(item.quantity)))
                },
                alreadyRefundedPoints = refundRepository.findByOrderIdAndStatusNot(request.orderId, RefundStatus.REJECTED)
                    .fold(BigDecimal.ZERO) { acc, it -> acc.add(it.pointRefundAmount) },
                isFullRefund = isFullRefund
            )

            val firstOrderItem = orderItemMap[request.items.first().orderItemId]!!
            val sellerId = firstOrderItem.sellerId

            val refund = Refund.create(
                orderId = request.orderId,
                paymentId = payment.id,
                buyerId = buyerId,
                sellerId = sellerId,
                reason = request.reason,
                reasonDetail = request.reasonDetail,
                refundAmount = refundAmount,
                isFullRefund = isFullRefund,
                returnShippingFee = returnShippingFee,
                pointRefundAmount = pointRefundAmount
            )
            val savedRefund = refundRepository.save(refund)

            val refundItems = request.items.mapIndexed { index, item ->
                val orderItem = orderItemMap[item.orderItemId]!!
                RefundItem.create(
                    refundId = savedRefund.id!!,
                    orderItemId = item.orderItemId,
                    productId = orderItem.productId,
                    productName = orderItem.productName,
                    productPrice = orderItem.productPrice,
                    quantity = item.quantity,
                    refundPrice = refundPrices[index]
                )
            }
            val savedRefundItems = refundItemRepository.saveAll(refundItems)

            val isAutoApprove = !request.reason.isSellerFault &&
                (shipping == null || shipping.status == ShippingStatus.PREPARING)

            if (isAutoApprove) {
                savedRefund.approve(buyerId)
                publishRefundCompletedEvent(savedRefund, savedRefundItems, payment)
                savedRefund.complete()
                refundRepository.save(savedRefund)
            }

            RefundResponse.from(savedRefund, savedRefundItems)
        }!!
    }

    override fun approveRefund(refundId: Long, approverId: Long): RefundResponse {
        val paymentId = (refundRepository.findByIdOrNull(refundId) ?: throw RefundNotFoundException()).paymentId
        val payment = paymentQueryPort.findById(paymentId) ?: throw RefundPaymentNotFoundException()

        return transactionTemplate.execute { _ ->
            val refund = refundRepository.findByIdOrNull(refundId) ?: throw RefundNotFoundException()

            if (refund.sellerId != approverId) {
                throw RefundAccessDeniedException()
            }

            refund.approve(approverId)

            val refundItems = refundItemRepository.findByRefundId(refundId)

            publishRefundCompletedEvent(refund, refundItems, payment)
            refund.complete()
            refundRepository.save(refund)

            RefundResponse.from(refund, refundItems)
        }!!
    }

    @Transactional
    override fun rejectRefund(refundId: Long, approverId: Long, request: RefundApprovalRequest): RefundResponse {
        val refund = refundRepository.findByIdOrNull(refundId) ?: throw RefundNotFoundException() 

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
        payment: PaymentInfo
    ) {
        refundEventPublisher.publishRefundCompletedEvent(
            RefundCompletedEvent(
                refundId = refund.id!!,
                orderId = refund.orderId,
                paymentId = payment.id,
                buyerId = refund.buyerId,
                refundAmount = refund.refundAmount,
                pointRefundAmount = refund.pointRefundAmount,
                isFullRefund = refund.isFullRefund,
                couponId = if (refund.isFullRefund) payment.couponId else null,
                items = refundItems.map { item ->
                    RefundItemEvent(
                        productId = item.productId,
                        quantity = item.quantity,
                        refundPrice = item.refundPrice,
                        orderItemId = item.orderItemId,
                        productPrice = item.productPrice
                    )
                }
            )
        )
    }

    companion object {
        private val IN_PROGRESS_STATUSES = setOf(RefundStatus.REQUESTED.name, RefundStatus.APPROVED.name)
    }
}
