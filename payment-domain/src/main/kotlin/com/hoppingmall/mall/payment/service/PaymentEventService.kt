package com.hoppingmall.mall.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.dto.event.MembershipUpdateRequestEvent
import com.hoppingmall.mall.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PaymentEventService(
    private val paymentEventPublisher: PaymentEventPublisher,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val pointEarnRateStrategy: PointEarnRateStrategy,
    private val objectMapper: ObjectMapper
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

    fun publishPaymentCompletedNotification(payment: Payment) {
        val eventId = payment.transactionId ?: "payment-${payment.id}"
        val metadata = objectMapper.writeValueAsString(
            mapOf(
            "orderId" to payment.orderId,
            "paymentId" to payment.id!!,
            "amount" to payment.amount.toString(),
            "method" to payment.method.toString(),
            "transactionId" to payment.transactionId
            )
        )
        
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentCompletedNotificationRequested",
            eventData = mapOf(
                "eventId" to eventId,
                "userId" to payment.userId,
                "type" to NotificationType.PAYMENT_COMPLETED.toString(),
                "title" to "결제가 완료되었습니다",
                "content" to "주문번호 ${payment.orderId}의 결제가 성공적으로 완료되었습니다. 결제 금액: ${payment.amount}원",
                "metadata" to metadata
            ),
            topic = "notification",
            partitionKey = payment.userId.toString()
        )
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

    fun publishPaymentFailedNotification(payment: Payment) {
        val eventId = "payment-failed-notification-${payment.id}"
        val metadata = objectMapper.writeValueAsString(
            mapOf(
                "orderId" to payment.orderId,
                "paymentId" to payment.id!!,
                "amount" to payment.amount.toString(),
                "reason" to (payment.errorMessage ?: "결제 실패")
            )
        )

        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentFailedNotificationRequested",
            eventData = mapOf(
                "eventId" to eventId,
                "userId" to payment.userId,
                "type" to NotificationType.PAYMENT_FAILED.toString(),
                "title" to "결제가 실패했습니다",
                "content" to "주문번호 ${payment.orderId}의 결제가 실패했습니다. 사유: ${payment.errorMessage ?: "결제 실패"}",
                "metadata" to metadata
            ),
            topic = "notification",
            partitionKey = payment.userId.toString()
        )
    }

    fun publishPaymentCancelledNotification(payment: Payment) {
        val eventId = "payment-cancelled-notification-${payment.id}"
        val metadata = objectMapper.writeValueAsString(
            mapOf(
                "orderId" to payment.orderId,
                "paymentId" to payment.id!!,
                "amount" to payment.amount.toString(),
                "transactionId" to payment.transactionId
            )
        )

        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentCancelledNotificationRequested",
            eventData = mapOf(
                "eventId" to eventId,
                "userId" to payment.userId,
                "type" to NotificationType.PAYMENT_CANCELLED.toString(),
                "title" to "결제가 취소되었습니다",
                "content" to "주문번호 ${payment.orderId}의 결제가 취소되었습니다. 취소 금액: ${payment.amount}원",
                "metadata" to metadata
            ),
            topic = "notification",
            partitionKey = payment.userId.toString()
        )
    }
} 
