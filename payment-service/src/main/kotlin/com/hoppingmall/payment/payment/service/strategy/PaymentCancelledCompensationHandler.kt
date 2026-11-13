package com.hoppingmall.payment.payment.service.strategy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.exception.EarnHistoryNotYetRecordedException
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import com.hoppingmall.payment.payment.service.RefundPointsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PaymentCancelledCompensationHandler(
    private val compensationEventLogService: CompensationEventLogService,
    private val refundPointsService: RefundPointsService,
    private val objectMapper: ObjectMapper
) : CompensationEventHandler {

    private val logger = LoggerFactory.getLogger(PaymentCancelledCompensationHandler::class.java)

    override fun supports(eventType: String): Boolean = eventType == "PaymentCancelled"

    override fun handle(node: JsonNode) {
        val event = objectMapper.treeToValue(node, PaymentCancelledEvent::class.java)
        try {
            require(event.eventId.isNotBlank()) { "결제 취소 보상 이벤트의 eventId가 비어 있음: orderId=${event.orderId}" }
            val log = compensationEventLogService.saveIfAbsent(
                eventId = event.eventId,
                compensationType = "PAYMENT_CANCELLED",
                paymentId = event.paymentId,
                orderId = event.orderId
            )
            if (log.isCompleted()) {
                logger.info("이미 처리 완료된 보상 이벤트: ${event.eventId}")
                return
            }

            refundPointsService.refundPoints(event.userId, event.paymentId)
            compensationEventLogService.markCompleted(event.eventId)
            logger.info("결제 취소 보상 처리 완료: orderId=${event.orderId}")
        } catch (e: EarnHistoryNotYetRecordedException) {
            logger.warn("결제 취소 보상 처리 보류(적립 이력이 아직 없어 다시 시도): ${event.eventId}, paymentId=${event.paymentId}")
            throw e
        } catch (e: Exception) {
            logger.error("결제 취소 보상 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }
}
