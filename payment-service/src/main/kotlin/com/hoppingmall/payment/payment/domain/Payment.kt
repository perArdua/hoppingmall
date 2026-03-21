package com.hoppingmall.payment.payment.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import jakarta.persistence.*
import org.hibernate.annotations.Filter
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
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
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
        fun create(
            orderId: Long,
            userId: Long,
            amount: BigDecimal,
            method: PaymentMethod,
            pointAmount: BigDecimal = BigDecimal.ZERO,
            couponId: Long? = null,
            couponDiscountAmount: BigDecimal = BigDecimal.ZERO
        ): Payment {
            return Payment(
                orderId = orderId,
                userId = userId,
                amount = amount,
                method = method,
                pointAmount = pointAmount,
                couponId = couponId,
                couponDiscountAmount = couponDiscountAmount
            )
        }
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
        status: PaymentStatus,
        transactionId: String? = null,
        completedAt: LocalDateTime? = null,
        errorMessage: String? = null
    ) {
        this.status = status
        this.transactionId = transactionId
        this.completedAt = completedAt
        this.errorMessage = errorMessage
    }

    fun isSuccess(): Boolean {
        return status == PaymentStatus.SUCCESS
    }

    fun isFailed(): Boolean {
        return status == PaymentStatus.FAILED
    }
}
