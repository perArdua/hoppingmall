package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent

interface PaymentEventPublisher {
    fun publishPaymentCompletedEvent(event: PaymentCompletedEvent)
    fun publishPointEarnRequestEvent(event: PointEarnRequestEvent)
} 