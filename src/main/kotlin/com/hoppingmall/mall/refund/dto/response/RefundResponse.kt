package com.hoppingmall.mall.refund.dto.response

import com.hoppingmall.mall.refund.domain.Refund
import com.hoppingmall.mall.refund.domain.RefundItem
import com.hoppingmall.mall.refund.enum.RefundReason
import com.hoppingmall.mall.refund.enum.RefundStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class RefundResponse(
    val id: Long,
    val orderId: Long,
    val paymentId: Long,
    val buyerId: Long,
    val sellerId: Long,
    val status: RefundStatus,
    val reason: RefundReason,
    val reasonDetail: String?,
    val refundAmount: BigDecimal,
    val isFullRefund: Boolean,
    val rejectionReason: String?,
    val approvedBy: Long?,
    val completedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val items: List<RefundItemResponse>
) {
    companion object {
        fun from(refund: Refund, items: List<RefundItem>): RefundResponse {
            return RefundResponse(
                id = refund.id!!,
                orderId = refund.orderId,
                paymentId = refund.paymentId,
                buyerId = refund.buyerId,
                sellerId = refund.sellerId,
                status = refund.status,
                reason = refund.reason,
                reasonDetail = refund.reasonDetail,
                refundAmount = refund.refundAmount,
                isFullRefund = refund.isFullRefund,
                rejectionReason = refund.rejectionReason,
                approvedBy = refund.approvedBy,
                completedAt = refund.completedAt,
                createdAt = refund.createdAt,
                updatedAt = refund.updatedAt ?: refund.createdAt,
                items = items.map { RefundItemResponse.from(it) }
            )
        }
    }
}
