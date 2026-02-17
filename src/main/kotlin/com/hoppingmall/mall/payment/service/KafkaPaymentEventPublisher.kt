package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.MembershipUpdateRequestEvent
import com.hoppingmall.mall.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import org.springframework.stereotype.Service

@Service
class KafkaPaymentEventPublisher(
    private val transactionalEventPublisher: TransactionalEventPublisher
) : PaymentEventPublisher {
    
    override fun publishPaymentCompletedEvent(event: PaymentCompletedEvent) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
            eventType = "PaymentCompleted",
            eventData = mapOf(
                "paymentId" to event.paymentId,
                "orderId" to event.orderId,
                "userId" to event.userId,
                "amount" to event.amount,
                "pointAmount" to event.pointAmount,
                "method" to event.method,
                "status" to event.status,
                "transactionId" to event.transactionId,
                "completedAt" to event.completedAt
            ),
            topic = "payment",
            partitionKey = event.paymentId.toString()
        )
    }
    
    override fun publishPointEarnRequestEvent(event: PointEarnRequestEvent) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
            eventType = "PointEarnRequested",
            eventData = mapOf(
                "eventId" to event.eventId,
                "userId" to event.userId,
                "orderId" to event.orderId,
                "paymentId" to event.paymentId,
                "earnAmount" to event.earnAmount,
                "reason" to (event.reason ?: "결제 완료 적립")
            ),
            topic = "point-earn-request",
            partitionKey = event.userId.toString()
        )
    }

    override fun publishMembershipUpdateEvent(event: MembershipUpdateRequestEvent) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
            eventType = "MembershipUpdateRequested",
            eventData = mapOf(
                "eventId" to event.eventId,
                "userId" to event.userId,
                "orderId" to event.orderId,
                "paymentId" to event.paymentId,
                "amount" to event.amount
            ),
            topic = "membership-update-request",
            partitionKey = event.userId.toString()
        )
    }

    override fun publishPaymentFailedEvent(event: PaymentFailedEvent) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
            eventType = "PaymentFailed",
            eventData = mapOf(
                "eventId" to event.eventId,
                "paymentId" to event.paymentId,
                "orderId" to event.orderId,
                "userId" to event.userId,
                "amount" to event.amount,
                "reason" to event.reason
            ),
            topic = "payment-compensation",
            partitionKey = event.orderId.toString()
        )
    }

    override fun publishPaymentCancelledEvent(event: PaymentCancelledEvent) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
            eventType = "PaymentCancelled",
            eventData = mapOf(
                "eventId" to event.eventId,
                "paymentId" to event.paymentId,
                "orderId" to event.orderId,
                "userId" to event.userId,
                "amount" to event.amount,
                "transactionId" to event.transactionId
            ),
            topic = "payment-compensation",
            partitionKey = event.orderId.toString()
        )
    }
}
