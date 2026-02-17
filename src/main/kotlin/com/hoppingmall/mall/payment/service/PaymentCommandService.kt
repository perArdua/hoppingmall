package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.request.PaymentRequest
import com.hoppingmall.mall.payment.dto.response.PaymentResponse

interface PaymentCommandService {
    fun processPayment(paymentRequest: PaymentRequest, userId: Long): PaymentResponse
    fun cancelPayment(paymentId: Long, userId: Long): PaymentResponse
} 