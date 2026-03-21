package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.dto.event.MembershipUpdateRequestEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.payment.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PaymentEventService(
    private val paymentEventPublisher: PaymentEventPublisher,
    private val pointEarnRateStrategy: PointEarnRateStrategy
) {

    fun publishPaymentCompletedEvent(payment: Payment) {
        val event = PaymentCompletedEvent(
            paymentId = payment.id!!,
            orderId = payment.orderId,
            userId = payment.userId,
            amount = payment.amount,
            pointAmount = payment.pointAmount,
            method = payment.method,
            status = payment.status,
            transactionId = payment.transactionId!!,
            completedAt = payment.completedAt ?: LocalDateTime.now()
        )
        paymentEventPublisher.publishPaymentCompletedEvent(event)
    }

    fun publishPointEarnRequestEvent(payment: Payment) {
        val earnRate = pointEarnRateStrategy.getEarnRate(payment.userId)
        val earnAmount = payment.amount.multiply(earnRate)
        val eventId = payment.transactionId ?: "payment-${payment.id}"

        val event = PointEarnRequestEvent(
            eventId = eventId,
            userId = payment.userId,
            orderId = payment.orderId,
            paymentId = payment.id!!,
            earnAmount = earnAmount
        )
        paymentEventPublisher.publishPointEarnRequestEvent(event)
    }

    fun publishMembershipUpdateEvent(payment: Payment) {
        val eventId = "membership-${payment.transactionId ?: "payment-${payment.id}"}"

        val event = MembershipUpdateRequestEvent(
            eventId = eventId,
            userId = payment.userId,
            orderId = payment.orderId,
            paymentId = payment.id!!,
            amount = payment.amount
        )
        paymentEventPublisher.publishMembershipUpdateEvent(event)
    }

    fun publishPaymentFailedEvent(payment: Payment) {
        val eventId = "payment-failed-${payment.id}"
        val event = PaymentFailedEvent(
            eventId = eventId,
            paymentId = payment.id!!,
            orderId = payment.orderId,
            userId = payment.userId,
            amount = payment.amount,
            reason = payment.errorMessage ?: "결제 실패"
        )
        paymentEventPublisher.publishPaymentFailedEvent(event)
    }

    fun publishPaymentCancelledEvent(payment: Payment) {
        val eventId = "payment-cancelled-${payment.id}"
        val event = PaymentCancelledEvent(
            eventId = eventId,
            paymentId = payment.id!!,
            orderId = payment.orderId,
            userId = payment.userId,
            amount = payment.amount,
            transactionId = payment.transactionId!!
        )
        paymentEventPublisher.publishPaymentCancelledEvent(event)
    }
}
