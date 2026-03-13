package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.refund.api.RefundInfo
import com.hoppingmall.mall.refund.api.RefundQueryPort
import com.hoppingmall.mall.refund.domain.repository.RefundRepository
import com.hoppingmall.mall.refund.enum.RefundStatus
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RefundQueryPortAdapter(
    private val refundRepository: RefundRepository
) : RefundQueryPort {

    override fun findCompletedBySellerAndPeriod(
        sellerId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<RefundInfo> {
        return refundRepository.findBySellerIdAndStatusAndCompletedAtBetween(
            sellerId, RefundStatus.COMPLETED, startDate, endDate
        ).map { refund ->
            RefundInfo(
                id = refund.id!!,
                refundAmount = refund.refundAmount
            )
        }
    }
}
