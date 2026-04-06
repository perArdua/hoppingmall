package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.dto.response.PaymentResponse

interface PaymentCommandService {
    fun processPayment(paymentRequest: PaymentRequest, userId: Long): PaymentResponse
    fun cancelPayment(paymentId: Long, userId: Long): PaymentResponse
    fun cancelPaymentInternal(paymentId: Long): PaymentResponse
}
