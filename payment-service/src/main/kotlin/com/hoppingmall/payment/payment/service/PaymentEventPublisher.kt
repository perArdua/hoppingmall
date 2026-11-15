package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.dto.event.MembershipRevertRequestEvent
import com.hoppingmall.payment.payment.dto.event.MembershipUpdateRequestEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.dto.event.PointEarnRequestEvent

interface PaymentEventPublisher {
    fun publishPaymentCompletedEvent(event: PaymentCompletedEvent)
    fun publishPointEarnRequestEvent(event: PointEarnRequestEvent)
    fun publishMembershipUpdateEvent(event: MembershipUpdateRequestEvent)
    fun publishMembershipRevertEvent(event: MembershipRevertRequestEvent)
    fun publishPaymentCancelledEvent(event: PaymentCancelledEvent)
}
