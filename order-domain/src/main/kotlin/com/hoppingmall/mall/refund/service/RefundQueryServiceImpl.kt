package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.refund.domain.repository.RefundItemRepository
import com.hoppingmall.mall.refund.domain.repository.RefundRepository
import com.hoppingmall.mall.refund.dto.response.RefundResponse
import com.hoppingmall.mall.refund.exception.RefundAccessDeniedException
import com.hoppingmall.mall.refund.exception.RefundNotFoundException
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
        return refundRepository.findByBuyerId(buyerId, pageable).map { refund ->
            val items = refundItemRepository.findByRefundId(refund.id!!)
            RefundResponse.from(refund, items)
        }
    }

    override fun getSellerRefunds(sellerId: Long, pageable: Pageable): Page<RefundResponse> {
        return refundRepository.findBySellerId(sellerId, pageable).map { refund ->
            val items = refundItemRepository.findByRefundId(refund.id!!)
            RefundResponse.from(refund, items)
        }
    }
}
