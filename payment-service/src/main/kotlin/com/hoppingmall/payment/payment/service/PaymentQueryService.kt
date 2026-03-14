package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PaymentQueryService {
    fun getPaymentById(paymentId: Long, userId: Long): PaymentResponse

    fun getPaymentsByUserId(userId: Long, pageable: Pageable): Page<PaymentResponse>

    fun getPaymentsByOrderId(orderId: Long, userId: Long): List<PaymentResponse>

    fun getPaymentByTransactionId(transactionId: String): PaymentResponse?
}
