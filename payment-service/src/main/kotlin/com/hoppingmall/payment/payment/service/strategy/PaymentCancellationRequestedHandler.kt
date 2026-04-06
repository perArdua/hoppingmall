package com.hoppingmall.payment.payment.service.strategy

import com.fasterxml.jackson.databind.JsonNode
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.payment.exception.PaymentNotFoundException
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import com.hoppingmall.payment.payment.service.PaymentCommandService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PaymentCancellationRequestedHandler(
    private val compensationEventLogService: CompensationEventLogService,
    private val paymentRepository: PaymentRepository,
    private val paymentCommandService: PaymentCommandService,
    private val transactionalEventPublisher: TransactionalEventPublisher
) : CompensationEventHandler {

    private val logger = LoggerFactory.getLogger(PaymentCancellationRequestedHandler::class.java)

    override fun supports(eventType: String): Boolean = eventType == "PaymentCancellationRequested"

    override fun handle(node: JsonNode) {
        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return

        try {
            val log = compensationEventLogService.saveIfAbsent(
                eventId = eventId,
                compensationType = "PAYMENT_CANCELLATION_REQUESTED",
                paymentId = 0L,
                orderId = orderId
            )
            if (log.isCompleted()) {
                logger.info("이미 처리 완료된 결제 취소 요청: $eventId")
                return
            }

            val payment = paymentRepository.findByOrderId(orderId)
            if (payment == null) {
                publishFailedEvent(eventId, orderId, "결제를 찾을 수 없음")
                compensationEventLogService.markCompleted(eventId)
                return
            }

            try {
                paymentCommandService.cancelPaymentInternal(payment.id!!)
                publishCompletedEvent(eventId, orderId, payment.id!!)
                compensationEventLogService.markCompleted(eventId)
                logger.info("결제 취소 처리 완료: orderId=$orderId, paymentId=${payment.id}")
            } catch (e: PaymentInvalidStateException) {
                publishFailedEvent(eventId, orderId, "결제 상태 부적합: ${payment.status}")
                compensationEventLogService.markCompleted(eventId)
                logger.warn("결제 취소 불가 상태: orderId=$orderId, status=${payment.status}")
            }

        } catch (e: Exception) {
            logger.error("결제 취소 요청 처리 실패: $eventId, 오류: ${e.message}")
            throw e
        }
    }

    private fun publishCompletedEvent(eventId: String, orderId: Long, paymentId: Long) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = paymentId.toString(),
            eventType = "PaymentCancellationCompleted",
            eventData = mapOf(
                "eventType" to "PaymentCancellationCompleted",
                "eventId" to "cancel-completed-$eventId",
                "orderId" to orderId,
                "paymentId" to paymentId
            ),
            topic = KafkaTopics.PAYMENT_COMPENSATION,
            partitionKey = orderId.toString()
        )
    }

    private fun publishFailedEvent(eventId: String, orderId: Long, reason: String) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = orderId.toString(),
            eventType = "PaymentCancellationFailed",
            eventData = mapOf(
                "eventType" to "PaymentCancellationFailed",
                "eventId" to "cancel-failed-$eventId",
                "orderId" to orderId,
                "reason" to reason
            ),
            topic = KafkaTopics.PAYMENT_COMPENSATION,
            partitionKey = orderId.toString()
        )
    }
}
