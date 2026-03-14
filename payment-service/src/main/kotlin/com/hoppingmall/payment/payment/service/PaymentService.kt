package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.dto.PaymentResult

interface PaymentService {
    fun processPayment(payment: Payment): PaymentResult
}
