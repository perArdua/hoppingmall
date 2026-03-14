package com.hoppingmall.order.refund.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaRefundEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : RefundEventPublisher {

    override fun publishRefundCompletedEvent(event: RefundCompletedEvent) {
        val payload = objectMapper.writeValueAsString(event)
        kafkaTemplate.send("refund-completion", event.refundId.toString(), payload)
    }
}
