package com.hoppingmall.order.refund.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.refund.exception.RefundInvalidStatusException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "refunds",
    indexes = [
        Index(name = "idx_refunds_order_id", columnList = "orderId"),
        Index(name = "idx_refunds_payment_id", columnList = "paymentId"),
        Index(name = "idx_refunds_buyer_id", columnList = "buyerId"),
        Index(name = "idx_refunds_seller_id", columnList = "sellerId")
    ]
)
class Refund private constructor(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false)
    val buyerId: Long,

    @Column(nullable = false)
    val sellerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RefundStatus = RefundStatus.REQUESTED,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val reason: RefundReason,

    @Column
    val reasonDetail: String? = null,

    @Column(nullable = false, precision = 10, scale = 2)
    val refundAmount: BigDecimal,

    @Column(nullable = false)
    val isFullRefund: Boolean,

    @Column
    var rejectionReason: String? = null,

    @Column
    var approvedBy: Long? = null,

    @Column
    var completedAt: LocalDateTime? = null
) : BaseEntity() {

    companion object {
        private val allowedTransitions: Map<RefundStatus, Set<RefundStatus>> = mapOf(
            RefundStatus.REQUESTED to setOf(RefundStatus.APPROVED, RefundStatus.REJECTED),
            RefundStatus.APPROVED to setOf(RefundStatus.COMPLETED),
            RefundStatus.COMPLETED to emptySet(),
            RefundStatus.REJECTED to emptySet()
        )

        fun create(
            orderId: Long,
            paymentId: Long,
            buyerId: Long,
            sellerId: Long,
            reason: RefundReason,
            reasonDetail: String?,
            refundAmount: BigDecimal,
            isFullRefund: Boolean
        ): Refund {
            return Refund(
                orderId = orderId,
                paymentId = paymentId,
                buyerId = buyerId,
                sellerId = sellerId,
                reason = reason,
                reasonDetail = reasonDetail,
                refundAmount = refundAmount,
                isFullRefund = isFullRefund
            )
        }
    }

    fun approve(approvedBy: Long) {
        validateTransition(RefundStatus.APPROVED)
        this.status = RefundStatus.APPROVED
        this.approvedBy = approvedBy
    }

    fun reject(reason: String, rejectedBy: Long) {
        validateTransition(RefundStatus.REJECTED)
        this.status = RefundStatus.REJECTED
        this.rejectionReason = reason
        this.approvedBy = rejectedBy
    }

    fun complete() {
        validateTransition(RefundStatus.COMPLETED)
        this.status = RefundStatus.COMPLETED
        this.completedAt = LocalDateTime.now()
    }

    private fun validateTransition(newStatus: RefundStatus) {
        val allowed = allowedTransitions[this.status] ?: emptySet()
        if (newStatus !in allowed) {
            throw RefundInvalidStatusException()
        }
    }
}
