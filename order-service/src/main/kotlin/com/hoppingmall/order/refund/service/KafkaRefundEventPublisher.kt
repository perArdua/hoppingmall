package com.hoppingmall.order.refund.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent
import org.springframework.stereotype.Component

@Component
class KafkaRefundEventPublisher(
    private val transactionalEventPublisher: TransactionalEventPublisherPort
) : RefundEventPublisher {

    override fun publishRefundCompletedEvent(event: RefundCompletedEvent) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Refund",
            aggregateId = event.refundId.toString(),
            eventType = "RefundCompleted",
            eventData = mapOf(
                "eventType" to "RefundCompleted",
                "eventId" to event.eventId,
                "refundId" to event.refundId,
                "orderId" to event.orderId,
                "paymentId" to event.paymentId,
                "buyerId" to event.buyerId,
                "refundAmount" to event.refundAmount,
                "pointRefundAmount" to event.pointRefundAmount,
                "isFullRefund" to event.isFullRefund
            ),
            topic = KafkaTopics.REFUND_COMPLETION,
            partitionKey = event.refundId.toString()
        )
    }
}
