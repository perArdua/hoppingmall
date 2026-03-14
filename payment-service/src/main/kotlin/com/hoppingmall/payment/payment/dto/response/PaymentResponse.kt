package com.hoppingmall.payment.payment.dto.response

import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentResponse(
    val id: Long,
    val orderId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val pointAmount: BigDecimal,
    val couponId: Long?,
    val couponDiscountAmount: BigDecimal,
    val method: String,
    val status: PaymentStatus,
    val transactionId: String?,
    val errorMessage: String?,
    val completedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(payment: Payment): PaymentResponse {
            return PaymentResponse(
                id = payment.id ?: throw PaymentInvalidStateException(),
                orderId = payment.orderId,
                userId = payment.userId,
                amount = payment.amount,
                pointAmount = payment.pointAmount,
                couponId = payment.couponId,
                couponDiscountAmount = payment.couponDiscountAmount,
                method = payment.method.name,
                status = payment.status,
                transactionId = payment.transactionId,
                errorMessage = payment.errorMessage,
                completedAt = payment.completedAt,
                createdAt = payment.createdAt,
                updatedAt = payment.updatedAt ?: payment.createdAt
            )
        }
    }
}
