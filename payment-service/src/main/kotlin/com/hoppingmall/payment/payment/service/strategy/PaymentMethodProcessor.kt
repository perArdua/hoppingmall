package com.hoppingmall.payment.payment.service.strategy

import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.enum.PaymentMethod

interface PaymentMethodProcessor {
    val method: PaymentMethod
    fun process(event: PaymentCompletedEvent)
}
