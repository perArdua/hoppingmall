package com.hoppingmall.payment.payment.service.strategy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PaymentFailedCompensationHandler(
    private val compensationEventLogService: CompensationEventLogService,
    private val objectMapper: ObjectMapper
) : CompensationEventHandler {

    private val logger = LoggerFactory.getLogger(PaymentFailedCompensationHandler::class.java)

    override fun supports(eventType: String): Boolean = eventType == "PaymentFailed"

    override fun handle(node: JsonNode) {
        val event = objectMapper.treeToValue(node, PaymentFailedEvent::class.java)
        try {
            val log = compensationEventLogService.saveIfAbsent(
                eventId = event.eventId,
                compensationType = "PAYMENT_FAILED",
                paymentId = event.paymentId,
                orderId = event.orderId
            )
            if (log.isCompleted()) {
                logger.info("이미 처리 완료된 보상 이벤트: ${event.eventId}")
                return
            }

            compensationEventLogService.markCompleted(event.eventId)
            logger.info("결제 실패 보상 처리 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 실패 보상 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }
}
