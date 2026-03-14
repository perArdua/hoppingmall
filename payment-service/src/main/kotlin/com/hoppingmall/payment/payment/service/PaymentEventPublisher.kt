package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.dto.event.MembershipUpdateRequestEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.payment.payment.dto.event.PointEarnRequestEvent

interface PaymentEventPublisher {
    fun publishPaymentCompletedEvent(event: PaymentCompletedEvent)
    fun publishPointEarnRequestEvent(event: PointEarnRequestEvent)
    fun publishMembershipUpdateEvent(event: MembershipUpdateRequestEvent)
    fun publishPaymentFailedEvent(event: PaymentFailedEvent)
    fun publishPaymentCancelledEvent(event: PaymentCancelledEvent)
}
