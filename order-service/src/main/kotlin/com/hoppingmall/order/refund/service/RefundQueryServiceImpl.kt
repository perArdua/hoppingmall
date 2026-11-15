package com.hoppingmall.order.refund.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.dto.response.OrderItemRefundResponse
import com.hoppingmall.order.refund.dto.response.OrderRefundsResponse
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.refund.exception.RefundAccessDeniedException
import com.hoppingmall.order.refund.exception.RefundNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RefundQueryServiceImpl(
    private val refundRepository: RefundRepository,
    private val refundItemRepository: RefundItemRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository
) : RefundQueryService {

    override fun getRefund(refundId: Long, userId: Long): RefundResponse {
        val refund = refundRepository.findByIdOrNull(refundId) ?: throw RefundNotFoundException() 
        if (refund.buyerId != userId && refund.sellerId != userId) {
            throw RefundAccessDeniedException()
        }
        val items = refundItemRepository.findByRefundId(refundId)
        return RefundResponse.from(refund, items)
    }

    override fun getMyRefunds(buyerId: Long, pageable: Pageable): Slice<RefundResponse> {
        val refundPage = refundRepository.findByBuyerId(buyerId, pageable)
        val refundIds = refundPage.content.mapNotNull { it.id }
        val itemsByRefundId = if (refundIds.isNotEmpty()) {
            refundItemRepository.findByRefundIdIn(refundIds).groupBy { it.refundId }
        } else emptyMap()
        return refundPage.map { refund ->
            RefundResponse.from(refund, itemsByRefundId[refund.id] ?: emptyList())
        }
    }

    override fun getSellerRefunds(sellerId: Long, pageable: Pageable): Slice<RefundResponse> {
        val refundPage = refundRepository.findBySellerId(sellerId, pageable)
        val refundIds = refundPage.content.mapNotNull { it.id }
        val itemsByRefundId = if (refundIds.isNotEmpty()) {
            refundItemRepository.findByRefundIdIn(refundIds).groupBy { it.refundId }
        } else emptyMap()
        return refundPage.map { refund ->
            RefundResponse.from(refund, itemsByRefundId[refund.id] ?: emptyList())
        }
    }

    override fun getOrderRefunds(orderId: Long, buyerId: Long): OrderRefundsResponse {
        val order = orderRepository.findByIdOrNull(orderId) ?: throw OrderNotFoundException()
        if (order.buyerId != buyerId) {
            throw RefundAccessDeniedException()
        }

        val quantities = refundItemRepository.findRefundQuantitiesByStatus(orderId)
        val refundedByItem = quantities.filter { it.status == RefundStatus.COMPLETED.name }
            .groupBy { it.orderItemId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity }.toInt() }
        val inProgressByItem = quantities.filter { it.status in IN_PROGRESS_STATUSES }
            .groupBy { it.orderItemId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity }.toInt() }

        val items = orderItemRepository.findByOrderId(orderId).sortedBy { it.id }.map { item ->
            val refunded = refundedByItem[item.id] ?: 0
            val inProgress = inProgressByItem[item.id] ?: 0
            OrderItemRefundResponse(
                orderItemId = item.id!!,
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                refundedQuantity = refunded,
                inProgressQuantity = inProgress,
                refundableQuantity = (item.quantity - refunded - inProgress).coerceAtLeast(0)
            )
        }

        val refunds = refundRepository.findByOrderIdOrderByIdAsc(orderId)
        val itemsByRefundId = if (refunds.isNotEmpty()) {
            refundItemRepository.findByRefundIdIn(refunds.mapNotNull { it.id }).groupBy { it.refundId }
        } else emptyMap()

        return OrderRefundsResponse(
            orderId = orderId,
            items = items,
            refunds = refunds.map { RefundResponse.from(it, itemsByRefundId[it.id] ?: emptyList()) }
        )
    }

    companion object {
        private val IN_PROGRESS_STATUSES = setOf(RefundStatus.REQUESTED.name, RefundStatus.APPROVED.name)
    }
}
