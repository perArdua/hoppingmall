package com.hoppingmall.order.refund.service

import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.exception.RefundAccessDeniedException
import com.hoppingmall.order.refund.exception.RefundNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RefundQueryServiceImpl(
    private val refundRepository: RefundRepository,
    private val refundItemRepository: RefundItemRepository
) : RefundQueryService {

    override fun getRefund(refundId: Long, userId: Long): RefundResponse {
        val refund = refundRepository.findById(refundId)
            .orElseThrow { RefundNotFoundException() }
        if (refund.buyerId != userId && refund.sellerId != userId) {
            throw RefundAccessDeniedException()
        }
        val items = refundItemRepository.findByRefundId(refundId)
        return RefundResponse.from(refund, items)
    }

    override fun getMyRefunds(buyerId: Long, pageable: Pageable): Page<RefundResponse> {
        val refundPage = refundRepository.findByBuyerId(buyerId, pageable)
        val refundIds = refundPage.content.mapNotNull { it.id }
        val itemsByRefundId = if (refundIds.isNotEmpty()) {
            refundItemRepository.findByRefundIdIn(refundIds).groupBy { it.refundId }
        } else emptyMap()
        return refundPage.map { refund ->
            RefundResponse.from(refund, itemsByRefundId[refund.id] ?: emptyList())
        }
    }

    override fun getSellerRefunds(sellerId: Long, pageable: Pageable): Page<RefundResponse> {
        val refundPage = refundRepository.findBySellerId(sellerId, pageable)
        val refundIds = refundPage.content.mapNotNull { it.id }
        val itemsByRefundId = if (refundIds.isNotEmpty()) {
            refundItemRepository.findByRefundIdIn(refundIds).groupBy { it.refundId }
        } else emptyMap()
        return refundPage.map { refund ->
            RefundResponse.from(refund, itemsByRefundId[refund.id] ?: emptyList())
        }
    }
}
