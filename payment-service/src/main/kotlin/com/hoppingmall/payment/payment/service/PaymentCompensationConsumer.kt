package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.payment.exception.PaymentNotFoundException
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PaymentCompensationConsumer(
    private val compensationEventLogService: CompensationEventLogService,
    private val paymentCommandService: PaymentCommandService,
    private val compensationEventHandlerRegistry: CompensationEventHandlerRegistry,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(PaymentCompensationConsumer::class.java)

    @KafkaListener(topics = [KafkaTopics.PAYMENT_COMPENSATION], groupId = "payment-compensation-service")
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

    fun handleRetriesExhausted(message: String) {
        val node = runCatching { objectMapper.readTree(message) }.getOrNull() ?: return
        val eventType = node.get("eventType")?.asText() ?: return
        compensationEventHandlerRegistry.getHandler(eventType)?.onRetriesExhausted(node)
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_REVERSAL], groupId = "payment-compensation-service")
    fun handlePaymentReversal(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != "PaymentReversalRequested") {
            logger.warn("알 수 없는 역보상 이벤트 타입: $eventType")
            return
        }

        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return
        val userId = node.get("userId")?.asLong() ?: return
        val paymentId = node.get("paymentId")?.asLong()?.takeIf { it > 0 }
            ?: paymentCommandService.findPaymentIdByOrderId(orderId)
        if (paymentId == null) {
            logger.warn("역보상 메시지에 결제 ID가 없고 그 주문의 결제도 없어 역보상 생략: eventId=$eventId, orderId=$orderId")
            return
        }

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

            try {
                paymentCommandService.cancelPaymentInternal(paymentId)
            } catch (e: PaymentInvalidStateException) {
                logger.warn("취소할 수 없는 상태의 결제라 역보상 취소 생략: orderId=$orderId, paymentId=$paymentId")
            } catch (e: PaymentNotFoundException) {
                logger.warn("결제를 찾을 수 없어 역보상 취소 생략: orderId=$orderId, paymentId=$paymentId")
            }
            compensationEventLogService.markCompleted(eventId)

            logger.info("결제 역보상 처리 완료: orderId=$orderId, paymentId=$paymentId, userId=$userId")
        } catch (e: Exception) {
            logger.error("결제 역보상 처리 실패: $eventId, 오류: ${e.message}")
            throw e
        }
    }
}
