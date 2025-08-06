package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.dto.PaymentResult
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.exception.PaymentException
import com.hoppingmall.mall.payment.exception.PaymentInvalidAmountException
import com.hoppingmall.mall.payment.exception.code.PaymentErrorCode
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.random.Random

@Service
class MockPaymentService : PaymentService {
    
    override fun processPayment(payment: Payment): PaymentResult {
        validatePayment(payment)
        
        val isSuccess = Random.nextFloat() < 0.9f
        
        return if (isSuccess) {
            val transactionId = generateMockTransactionId()
            PaymentResult.success(
                payment = payment,
                transactionId = transactionId
            )
        } else {
            PaymentResult.failed(
                payment = payment,
                errorMessage = getRandomErrorMessage()
            )
        }
    }
    
    private fun validatePayment(payment: Payment) {
        if (payment.amount <= BigDecimal.ZERO) {
            throw PaymentInvalidAmountException()
        }
        if (payment.orderId <= 0) {
            throw PaymentException(PaymentErrorCode.PAYMENT_INVALID_ORDER)
        }
        if (payment.userId <= 0) {
            throw PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED)
        }
    }
    
    private fun generateMockTransactionId(): String {
        return "MOCK_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }
    
    private fun getRandomErrorMessage(): String {
        val errors = listOf(
            "잔액 부족",
            "카드 한도 초과",
            "카드 만료",
            "네트워크 오류",
            "서버 일시적 오류"
        )
        return errors.random()
    }
} 