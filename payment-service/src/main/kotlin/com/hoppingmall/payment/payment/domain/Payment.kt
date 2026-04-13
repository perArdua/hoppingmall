package com.hoppingmall.payment.payment.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_order_id", columnList = "orderId"),
        Index(name = "idx_payments_user_id", columnList = "userId")
    ]
)
class Payment private constructor(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val method: PaymentMethod,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(nullable = false, precision = 10, scale = 2)
    val pointAmount: BigDecimal = BigDecimal.ZERO,

    @Column(unique = true)
    var transactionId: String? = null,

    @Column
    var errorMessage: String? = null,

    @Column
    var completedAt: LocalDateTime? = null,

    @Column
    val couponId: Long? = null,

    @Column(precision = 10, scale = 2)
    val couponDiscountAmount: BigDecimal = BigDecimal.ZERO
) : BaseEntity() {

    companion object {
        private val allowedTransitions: Map<PaymentStatus, Set<PaymentStatus>> = mapOf(
            PaymentStatus.PENDING to setOf(PaymentStatus.SUCCESS, PaymentStatus.FAILED, PaymentStatus.CANCELLED),
            PaymentStatus.SUCCESS to setOf(PaymentStatus.CANCELLED, PaymentStatus.REFUNDED),
            PaymentStatus.FAILED to emptySet(),
            PaymentStatus.CANCELLED to emptySet(),
            PaymentStatus.REFUNDED to emptySet()
        )

        fun create(
            orderId: Long,
            userId: Long,
            amount: BigDecimal,
            method: PaymentMethod,
            pointAmount: BigDecimal = BigDecimal.ZERO,
            couponId: Long? = null,
            couponDiscountAmount: BigDecimal = BigDecimal.ZERO
        ): Payment =
            Payment(
                orderId = orderId,
                userId = userId,
                amount = amount,
                method = method,
                pointAmount = pointAmount,
                couponId = couponId,
                couponDiscountAmount = couponDiscountAmount
            )
    }

    fun copy(): Payment {
        return Payment(
            orderId = this.orderId,
            userId = this.userId,
            amount = this.amount,
            method = this.method,
            pointAmount = this.pointAmount,
            couponId = this.couponId,
            couponDiscountAmount = this.couponDiscountAmount
        ).apply {
            this.status = this@Payment.status
            this.transactionId = this@Payment.transactionId
            this.errorMessage = this@Payment.errorMessage
            this.completedAt = this@Payment.completedAt
        }
    }

    fun updateStatus(
        newStatus: PaymentStatus,
        transactionId: String? = null,
        completedAt: LocalDateTime? = null,
        errorMessage: String? = null
    ) {
        validateTransition(newStatus)
        this.status = newStatus
        this.transactionId = transactionId
        this.completedAt = completedAt
        this.errorMessage = errorMessage
    }

    private fun validateTransition(newStatus: PaymentStatus) {
        val allowed = allowedTransitions[this.status] ?: emptySet()
        if (newStatus !in allowed) {
            throw PaymentInvalidStateException()
        }
    }

    fun isSuccess(): Boolean = status == PaymentStatus.SUCCESS

    fun isFailed(): Boolean = status == PaymentStatus.FAILED
}
