package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.refund.dto.event.RefundCompletedEvent
import org.springframework.stereotype.Service

@Service
class KafkaRefundEventPublisher(
    private val transactionalEventPublisher: TransactionalEventPublisher
) : RefundEventPublisher {

    override fun publishRefundCompletedEvent(event: RefundCompletedEvent) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Refund",
            aggregateId = event.refundId.toString(),
            eventType = "RefundCompleted",
            eventData = mapOf(
                "eventId" to event.eventId,
                "refundId" to event.refundId,
                "orderId" to event.orderId,
                "paymentId" to event.paymentId,
                "buyerId" to event.buyerId,
                "refundAmount" to event.refundAmount,
                "pointRefundAmount" to event.pointRefundAmount,
                "isFullRefund" to event.isFullRefund,
                "items" to event.items.map { item ->
                    mapOf(
                        "productId" to item.productId,
                        "quantity" to item.quantity,
                        "refundPrice" to item.refundPrice
                    )
                }
            ),
            topic = "refund-completion",
            partitionKey = event.orderId.toString()
        )
    }
}
