package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PaymentCompensationConsumer(
    private val compensationEventLogService: CompensationEventLogService,
    private val refundPointsService: RefundPointsService,
    private val compensationEventHandlerRegistry: CompensationEventHandlerRegistry,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(PaymentCompensationConsumer::class.java)

    @KafkaListener(topics = ["payment-compensation"], groupId = "payment-compensation-service")
    fun handleCompensationEvent(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        val handler = eventType?.let { compensationEventHandlerRegistry.getHandler(it) }
        if (handler != null) {
            handler.handle(node)
        } else {
            logger.warn("알 수 없는 보상 이벤트 타입: $eventType")
        }
    }

    @KafkaListener(topics = ["payment-reversal"], groupId = "payment-compensation-service")
    fun handlePaymentReversal(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != "PaymentReversalRequested") {
            logger.warn("알 수 없는 역보상 이벤트 타입: $eventType")
            return
        }

        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return
        val paymentId = node.get("paymentId")?.asLong() ?: return
        val userId = node.get("userId")?.asLong() ?: return

        try {
            val log = compensationEventLogService.saveIfAbsent(
                eventId = eventId,
                compensationType = "PAYMENT_REVERSAL",
                paymentId = paymentId,
                orderId = orderId
            )
            if (log.isCompleted()) {
                logger.info("이미 처리 완료된 역보상 이벤트: $eventId")
                return
            }

            refundPointsService.refundPoints(userId, paymentId)
            compensationEventLogService.markCompleted(eventId)

            logger.info("결제 역보상 처리 완료: orderId=$orderId, paymentId=$paymentId")
        } catch (e: Exception) {
            logger.error("결제 역보상 처리 실패: $eventId, 오류: ${e.message}")
            throw e
        }
    }
}
