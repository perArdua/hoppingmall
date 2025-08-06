package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal
import java.time.LocalDateTime

fun Payment.Companion.fixture(
    orderId: Long = 1L,
    userId: Long = 1L,
    amount: BigDecimal = BigDecimal("50000"),
    method: PaymentMethod = PaymentMethod.CREDIT_CARD,
    pointAmount: BigDecimal = BigDecimal("1000"),
    status: PaymentStatus = PaymentStatus.PENDING,
    transactionId: String? = null,
    errorMessage: String? = null,
    completedAt: LocalDateTime? = null
): Payment {
    return Payment.create(
        orderId = orderId,
        userId = userId,
        amount = amount,
        method = method,
        pointAmount = pointAmount
    ).apply {
        this.status = status
        this.transactionId = transactionId
        this.errorMessage = errorMessage
        this.completedAt = completedAt
    }.withId(1L)
}

fun Payment.Companion.successFixture(
    orderId: Long = 1L,
    userId: Long = 1L,
    amount: BigDecimal = BigDecimal("50000"),
    method: PaymentMethod = PaymentMethod.CREDIT_CARD,
    pointAmount: BigDecimal = BigDecimal("1000"),
    transactionId: String = "TXN_123456",
    completedAt: LocalDateTime = LocalDateTime.now()
): Payment {
    return Payment.fixture(
        orderId = orderId,
        userId = userId,
        amount = amount,
        method = method,
        pointAmount = pointAmount,
        status = PaymentStatus.SUCCESS,
        transactionId = transactionId,
        completedAt = completedAt
    )
}

fun Payment.Companion.failedFixture(
    orderId: Long = 1L,
    userId: Long = 1L,
    amount: BigDecimal = BigDecimal("50000"),
    method: PaymentMethod = PaymentMethod.CREDIT_CARD,
    pointAmount: BigDecimal = BigDecimal("1000"),
    errorMessage: String = "잔액 부족"
): Payment {
    return Payment.fixture(
        orderId = orderId,
        userId = userId,
        amount = amount,
        method = method,
        pointAmount = pointAmount,
        status = PaymentStatus.FAILED,
        errorMessage = errorMessage
    )
} 