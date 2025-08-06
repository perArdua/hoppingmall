package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaPaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : PaymentEventPublisher {
    
    override fun publishPaymentCompletedEvent(event: PaymentCompletedEvent) {
        kafkaTemplate.send("payment-completed", event.paymentId.toString(), event)
    }
    
    override fun publishPointEarnRequestEvent(event: PointEarnRequestEvent) {
        kafkaTemplate.send("point-earn-request", event.userId.toString(), event)
    }
}