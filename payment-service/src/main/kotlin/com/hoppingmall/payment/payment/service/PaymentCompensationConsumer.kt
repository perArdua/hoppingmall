package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.repository.CompensationEventLogRepository
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import com.hoppingmall.payment.port.InventoryCommandPort
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.port.OrderQueryPort
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentCompensationConsumer(
    private val compensationEventLogRepository: CompensationEventLogRepository,
    private val orderCommandPort: OrderCommandPort,
    private val orderQueryPort: OrderQueryPort,
    private val inventoryCommandPort: InventoryCommandPort,
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(PaymentCompensationConsumer::class.java)

    @KafkaListener(topics = ["payment-compensation"], groupId = "payment-compensation-service")
    fun handleCompensationEvent(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        when (eventType) {
            "PaymentFailed" -> {
                val event = objectMapper.treeToValue(node, PaymentFailedEvent::class.java)
                handlePaymentFailed(event)
            }
            "PaymentCancelled" -> {
                val event = objectMapper.treeToValue(node, PaymentCancelledEvent::class.java)
                handlePaymentCancelled(event)
            }
            else -> logger.warn("알 수 없는 보상 이벤트 타입: $eventType")
        }
    }

    @Transactional
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        try {
            if (compensationEventLogRepository.existsByEventId(event.eventId)) {
                logger.info("이미 처리된 보상 이벤트: ${event.eventId}")
                return
            }

            cancelOrderAndRestoreStock(event.orderId)

            try {
                compensationEventLogRepository.save(
                    CompensationEventLog(
                        eventId = event.eventId,
                        compensationType = "PAYMENT_FAILED",
                        paymentId = event.paymentId,
                        orderId = event.orderId
                    )
                )
            } catch (e: DataIntegrityViolationException) {
                logger.info("이미 처리된 보상 이벤트: ${event.eventId}")
                return
            }

            logger.info("결제 실패 보상 처리 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 실패 보상 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }

    @Transactional
    fun handlePaymentCancelled(event: PaymentCancelledEvent) {
        try {
            if (compensationEventLogRepository.existsByEventId(event.eventId)) {
                logger.info("이미 처리된 보상 이벤트: ${event.eventId}")
                return
            }

            cancelOrderAndRestoreStock(event.orderId)
            refundPoints(event.userId, event.paymentId)

            try {
                compensationEventLogRepository.save(
                    CompensationEventLog(
                        eventId = event.eventId,
                        compensationType = "PAYMENT_CANCELLED",
                        paymentId = event.paymentId,
                        orderId = event.orderId
                    )
                )
            } catch (e: DataIntegrityViolationException) {
                logger.info("이미 처리된 보상 이벤트: ${event.eventId}")
                return
            }

            logger.info("결제 취소 보상 처리 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 취소 보상 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }

    private fun cancelOrderAndRestoreStock(orderId: Long) {
        val cancelled = orderCommandPort.cancelOrder(orderId)
        if (!cancelled) {
            logger.info("주문 취소 실패 또는 이미 취소됨: $orderId")
            return
        }

        val orderItems = orderQueryPort.findOrderItemsByOrderId(orderId)
        orderItems.forEach { item ->
            inventoryCommandPort.increaseStock(item.productId, item.quantity)
        }
    }

    private fun refundPoints(userId: Long, paymentId: Long) {
        val earnHistory = pointHistoryRepository.findByPaymentIdAndType(paymentId, PointType.EARN)
            ?: return

        val point = pointRepository.findByUserId(userId) ?: return

        point.usePoints(earnHistory.amount)
        pointRepository.save(point)

        pointHistoryRepository.save(
            PointHistory(
                userId = userId,
                amount = earnHistory.amount,
                type = PointType.REFUND,
                reason = "결제 취소 포인트 반환",
                paymentId = paymentId,
                eventId = "refund-$paymentId"
            )
        )
    }
}
