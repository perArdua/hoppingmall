package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.MembershipUpdateRequestEvent
import com.hoppingmall.mall.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent

interface PaymentEventPublisher {
    fun publishPaymentCompletedEvent(event: PaymentCompletedEvent)
    fun publishPointEarnRequestEvent(event: PointEarnRequestEvent)
    fun publishMembershipUpdateEvent(event: MembershipUpdateRequestEvent)
    fun publishPaymentFailedEvent(event: PaymentFailedEvent)
    fun publishPaymentCancelledEvent(event: PaymentCancelledEvent)
} 