package com.hoppingmall.mall.payment.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import jakarta.persistence.*
import org.hibernate.annotations.Filter
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
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
    var completedAt: LocalDateTime? = null
) : BaseEntity() {

    companion object {
        fun create(
            orderId: Long,
            userId: Long,
            amount: BigDecimal,
            method: PaymentMethod,
            pointAmount: BigDecimal = BigDecimal.ZERO
        ): Payment {
            return Payment(
                orderId = orderId,
                userId = userId,
                amount = amount,
                method = method,
                pointAmount = pointAmount
            )
        }
    }

    fun copy(): Payment {
        return Payment(
            orderId = this.orderId,
            userId = this.userId,
            amount = this.amount,
            method = this.method,
            pointAmount = this.pointAmount
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