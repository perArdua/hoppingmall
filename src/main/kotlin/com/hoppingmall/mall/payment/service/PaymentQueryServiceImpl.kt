package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.dto.response.PaymentResponse
import com.hoppingmall.mall.payment.exception.PaymentAccessDeniedException
import com.hoppingmall.mall.payment.exception.PaymentNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PaymentQueryServiceImpl(
    private val paymentRepository: PaymentRepository
) : PaymentQueryService {
    
    override fun getPaymentById(paymentId: Long, userId: Long): PaymentResponse {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException() }
        if (payment.userId != userId) {
            throw PaymentAccessDeniedException()
        }
        return PaymentResponse.from(payment)
    }
    
    override fun getPaymentsByUserId(userId: Long, pageable: Pageable): Page<PaymentResponse> {
        return paymentRepository.findByUserId(userId, pageable)
            .map { PaymentResponse.from(it) }
    }
    
    override fun getPaymentsByOrderId(orderId: Long, userId: Long): List<PaymentResponse> {
        val payment = paymentRepository.findByOrderId(orderId)
        return if (payment != null) {
            if (payment.userId != userId) {
                throw PaymentAccessDeniedException()
            }
            listOf(PaymentResponse.from(payment))
        } else {
            emptyList()
        }
    }
    
    override fun getPaymentByTransactionId(transactionId: String): PaymentResponse? {
        val payment = paymentRepository.findByTransactionId(transactionId)
        return payment?.let { PaymentResponse.from(it) }
    }
} 