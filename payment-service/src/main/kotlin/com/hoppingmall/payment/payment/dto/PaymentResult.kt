package com.hoppingmall.payment.payment.dto

import com.hoppingmall.payment.payment.domain.Payment
import java.time.LocalDateTime

data class PaymentResult(
    val payment: Payment,
    val isSuccess: Boolean,
    val transactionId: String? = null,
    val errorMessage: String? = null,
    val completedAt: LocalDateTime? = null
) {
    companion object {
        fun success(payment: Payment, transactionId: String): PaymentResult {
            return PaymentResult(
                payment = payment,
                isSuccess = true,
                transactionId = transactionId,
                completedAt = LocalDateTime.now()
            )
        }

        fun failed(payment: Payment, errorMessage: String): PaymentResult {
            return PaymentResult(
                payment = payment,
                isSuccess = false,
                errorMessage = errorMessage
            )
        }
    }
}
