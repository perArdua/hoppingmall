package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.dto.PaymentResult

interface PaymentService {
    fun processPayment(payment: Payment): PaymentResult
} 