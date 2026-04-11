package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class OrderCancellationResultConsumer(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val sagaEventLogRepository: SagaEventLogRepository,
    private val inventoryCommandPort: InventoryCommandPort,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(OrderCancellationResultConsumer::class.java)

    @KafkaListener(topics = [KafkaTopics.PAYMENT_COMPENSATION], groupId = "order-cancellation-result-service")
    fun handleCancellationResult(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText() ?: return

        when (eventType) {
            "PaymentCancellationCompleted" -> handleCompleted(node)
            "PaymentCancellationFailed" -> handleFailed(node)
            else -> return
        }
    }

    private fun handleCompleted(node: com.fasterxml.jackson.databind.JsonNode) {
        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return

        val existingLog = sagaEventLogRepository.findByEventId(eventId)
        if (existingLog != null && existingLog.isFullyCompleted()) {
            logger.info("이미 처리된 취소 완료 이벤트: $eventId")
            return
        }

        val cancelled = transactionTemplate.execute {
            val order = orderRepository.findByIdOrNull(orderId)
            if (order == null) {
                logger.error("주문을 찾을 수 없음: orderId=$orderId")
                return@execute false
            }

            if (order.status != OrderStatus.CANCEL_REQUESTED) {
                logger.warn("CANCEL_REQUESTED가 아닌 주문: orderId=$orderId, status=${order.status}")
                return@execute false
            }

            order.updateStatus(OrderStatus.CANCELLED)
            orderRepository.save(order)

            val log = existingLog ?: SagaEventLog(
                eventId = eventId,
                eventType = "PAYMENT_CANCELLATION_COMPLETED",
                orderId = orderId
            )
            log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            log.markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
            sagaEventLogRepository.save(log)

            true
        } ?: false

        if (cancelled) {
            val orderItems = orderItemRepository.findByOrderId(orderId)
            val reservationIds = orderItems.mapNotNull { it.reservationId }
            if (reservationIds.isNotEmpty()) {
                inventoryCommandPort.cancelReservations(reservationIds)
            } else {
                orderItems.forEach { item ->
                    inventoryCommandPort.increaseStock(item.productId, item.quantity)
                }
            }

            transactionalEventPublisher.publishEvent(
                aggregateType = "Order",
                aggregateId = orderId.toString(),
                eventType = "OrderCancellationNotificationRequested",
                eventData = mapOf(
                    "eventType" to "OrderCancellationNotificationRequested",
                    "eventId" to "notif-cancel-$eventId",
                    "userId" to (orderRepository.findByIdOrNull(orderId)?.buyerId ?: 0L),
                    "type" to "PAYMENT_CANCELLED",
                    "title" to "주문이 취소되었습니다",
                    "content" to "주문번호 ${orderId}의 결제 취소가 완료되었습니다."
                ),
                topic = KafkaTopics.NOTIFICATION,
                partitionKey = orderId.toString()
            )

            logger.info("결제 취소 완료 처리: orderId=$orderId, CANCELLED 전환 + 재고 복구")
        }
    }

    private fun handleFailed(node: com.fasterxml.jackson.databind.JsonNode) {
        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return
        val reason = node.get("reason")?.asText() ?: "알 수 없는 오류"

        val existingLog = sagaEventLogRepository.findByEventId(eventId)
        if (existingLog != null && existingLog.isFullyCompleted()) {
            logger.info("이미 처리된 취소 실패 이벤트: $eventId")
            return
        }

        transactionTemplate.execute {
            val order = orderRepository.findByIdOrNull(orderId)
            if (order == null) {
                logger.error("주문을 찾을 수 없음: orderId=$orderId")
                return@execute
            }

            if (order.status != OrderStatus.CANCEL_REQUESTED) {
                logger.warn("CANCEL_REQUESTED가 아닌 주문: orderId=$orderId, status=${order.status}")
                return@execute
            }

            order.updateStatus(OrderStatus.CANCEL_FAILED)
            orderRepository.save(order)

            val log = existingLog ?: SagaEventLog(
                eventId = eventId,
                eventType = "PAYMENT_CANCELLATION_FAILED",
                orderId = orderId
            )
            log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            log.markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
            sagaEventLogRepository.save(log)

            transactionalEventPublisher.publishEvent(
                aggregateType = "Order",
                aggregateId = orderId.toString(),
                eventType = "OrderCancellationFailedNotificationRequested",
                eventData = mapOf(
                    "eventType" to "OrderCancellationFailedNotificationRequested",
                    "eventId" to "notif-cancel-fail-$eventId",
                    "userId" to (order.buyerId),
                    "type" to "PAYMENT_CANCELLED",
                    "title" to "취소 처리에 실패했습니다",
                    "content" to "주문번호 ${orderId}의 결제 취소가 실패했습니다. 고객센터로 문의해주세요. 사유: $reason"
                ),
                topic = KafkaTopics.NOTIFICATION,
                partitionKey = orderId.toString()
            )
        }

        logger.warn("결제 취소 실패 처리: orderId=$orderId, CANCEL_FAILED 전환, 사유=$reason")
    }
}
