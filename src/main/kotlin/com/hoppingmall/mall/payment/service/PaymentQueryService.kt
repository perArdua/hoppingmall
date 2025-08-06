package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.response.PaymentResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PaymentQueryService {
    fun getPaymentById(paymentId: Long): PaymentResponse
    
    fun getPaymentsByUserId(userId: Long, pageable: Pageable): Page<PaymentResponse>
    
    fun getPaymentsByOrderId(orderId: Long): List<PaymentResponse>
    
    fun getPaymentByTransactionId(transactionId: String): PaymentResponse?
} 