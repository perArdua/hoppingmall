package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.payment.port.InventoryCommandPort
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.port.OrderQueryPort
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class PaymentCompensationConsumer(
    private val compensationEventLogService: CompensationEventLogService,
    private val orderCommandPort: OrderCommandPort,
    private val orderQueryPort: OrderQueryPort,
    private val inventoryCommandPort: InventoryCommandPort,
    private val refundPointsService: RefundPointsService,
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

    fun handlePaymentFailed(event: PaymentFailedEvent) {
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

            cancelOrderAndRestoreStock(event.orderId)
            compensationEventLogService.markCompleted(event.eventId)

            logger.info("결제 실패 보상 처리 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 실패 보상 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }

    fun handlePaymentCancelled(event: PaymentCancelledEvent) {
        try {
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

            cancelOrderAndRestoreStock(event.orderId)
            refundPointsService.refundPoints(event.userId, event.paymentId)
            compensationEventLogService.markCompleted(event.eventId)

            logger.info("결제 취소 보상 처리 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 취소 보상 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }

    private fun cancelOrderAndRestoreStock(orderId: Long) {
        val cancelled = orderCommandPort.cancelOrder(orderId)
        if (!cancelled) {
            logger.warn("주문이 이미 취소됨: $orderId")
        }

        val orderItems = orderQueryPort.findOrderItemsByOrderId(orderId)
        orderItems.forEach { item ->
            inventoryCommandPort.increaseStock(item.productId, item.quantity)
        }
    }
}
