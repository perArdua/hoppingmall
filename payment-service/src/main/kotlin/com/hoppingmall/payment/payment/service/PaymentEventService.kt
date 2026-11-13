package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.dto.event.MembershipRevertRequestEvent
import com.hoppingmall.payment.payment.dto.event.MembershipUpdateRequestEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.payment.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.payment.payment.enum.MembershipRevertReason
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PaymentEventService(
    private val paymentEventPublisher: PaymentEventPublisher
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

    fun publishPointEarnRequestEvent(payment: Payment, earnRate: BigDecimal) {
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

    fun publishMembershipCancelRevertEvent(payment: Payment) {
        publishMembershipRevertEvent(
            payment = payment,
            eventId = "membership-revert-cancel-${payment.id}",
            amount = payment.membershipCancelRevertAmount(),
            reason = MembershipRevertReason.CANCEL,
            refundId = null
        )
    }

    fun publishMembershipRefundRevertEvent(payment: Payment, refundId: Long, refundAmount: BigDecimal) {
        publishMembershipRevertEvent(
            payment = payment,
            eventId = "membership-revert-refund-$refundId",
            amount = refundAmount,
            reason = MembershipRevertReason.REFUND,
            refundId = refundId
        )
    }

    private fun publishMembershipRevertEvent(
        payment: Payment,
        eventId: String,
        amount: BigDecimal,
        reason: MembershipRevertReason,
        refundId: Long?
    ) {
        if (!isMembershipUpdateRequested(payment) || amount <= BigDecimal.ZERO) return

        val event = MembershipRevertRequestEvent(
            eventId = eventId,
            userId = payment.userId,
            orderId = payment.orderId,
            paymentId = payment.id!!,
            amount = amount,
            reason = reason,
            refundId = refundId
        )
        paymentEventPublisher.publishMembershipRevertEvent(event)
    }

    private fun isMembershipUpdateRequested(payment: Payment): Boolean =
        payment.transactionId != null && payment.amount > BigDecimal.ZERO

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
