package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.dto.event.PaymentCompletedEvent
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.TransactionalEventPublisherPort
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class OrderSagaConsumer(
    private val sagaEventLogRepository: SagaEventLogRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val inventoryCommandPort: InventoryCommandPort,
    private val transactionalEventPublisherPort: TransactionalEventPublisherPort,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(OrderSagaConsumer::class.java)

    @KafkaListener(topics = ["payment"], groupId = "order-saga-service")
    fun handlePaymentCompleted(message: String) {
        val event = objectMapper.readValue(message, PaymentCompletedEvent::class.java)
        val eventId = "payment-completed-${event.transactionId}"

        val existingLog = sagaEventLogRepository.findByEventId(eventId)
        if (existingLog != null && existingLog.isFullyCompleted()) {
            logger.info("이미 처리된 결제 완료 이벤트: $eventId")
            return
        }

        val reservationIds = if (existingLog == null || !existingLog.isStepCompleted(SagaEventLog.LOCAL_COMPLETED)) {
            executeLocalPhase(event, eventId)
        } else {
            reconstructReservationIds(event.orderId)
        }

        if (reservationIds == null) return

        executeRemotePhase(eventId, event, reservationIds)
    }

    private fun executeLocalPhase(event: PaymentCompletedEvent, eventId: String): List<String>? {
        return transactionTemplate.execute {
            val order = orderRepository.findById(event.orderId).orElse(null)
            if (order == null) {
                logger.error("주문을 찾을 수 없음: orderId=${event.orderId}")
                return@execute null
            }

            if (order.status != OrderStatus.CREATED) {
                logger.warn("주문 상태가 CREATED가 아님: orderId=${event.orderId}, status=${order.status}")
                val log = sagaEventLogRepository.findByEventId(eventId)
                    ?: SagaEventLog(eventId = eventId, eventType = "PAYMENT_COMPLETED", orderId = event.orderId)
                log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
                log.markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
                sagaEventLogRepository.save(log)
                return@execute null
            }

            order.updateStatus(OrderStatus.PAID)
            orderRepository.save(order)

            val log = SagaEventLog(eventId = eventId, eventType = "PAYMENT_COMPLETED", orderId = event.orderId)
            log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            sagaEventLogRepository.save(log)

            orderItemRepository.findByOrderId(event.orderId).mapNotNull { it.reservationId }
        }
    }

    private fun executeRemotePhase(eventId: String, event: PaymentCompletedEvent, reservationIds: List<String>) {
        try {
            val confirmed = if (reservationIds.isNotEmpty()) {
                inventoryCommandPort.confirmReservations(reservationIds)
            } else {
                true
            }

            if (confirmed) {
                transactionTemplate.execute {
                    val log = sagaEventLogRepository.findByEventId(eventId)!!
                    log.markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
                    sagaEventLogRepository.save(log)
                }
                logger.info("주문 결제 확정: orderId=${event.orderId}")
            } else {
                compensateFailedConfirmation(eventId, event)
            }
        } catch (e: Exception) {
            logger.error("재고 확정 실패, 보상 처리: orderId=${event.orderId}", e)
            compensateFailedConfirmation(eventId, event)
        }
    }

    @Transactional
    fun compensateFailedConfirmation(eventId: String, event: PaymentCompletedEvent) {
        val order = orderRepository.findById(event.orderId).orElse(null) ?: return
        if (order.status == OrderStatus.PAID && order.isCancellable()) {
            order.updateStatus(OrderStatus.CANCELLED)
            orderRepository.save(order)
        }

        transactionalEventPublisherPort.publishEvent(
            aggregateType = "Order",
            aggregateId = event.orderId.toString(),
            eventType = "PaymentReversalRequested",
            eventData = mapOf(
                "eventType" to "PaymentReversalRequested",
                "eventId" to "reversal-${event.transactionId}",
                "orderId" to event.orderId,
                "paymentId" to event.paymentId,
                "userId" to event.userId,
                "reason" to "RESERVATION_EXPIRED"
            ),
            topic = "payment-reversal",
            partitionKey = event.orderId.toString()
        )

        val log = sagaEventLogRepository.findByEventId(eventId)
        log?.markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
        log?.let { sagaEventLogRepository.save(it) }

        logger.warn("예약 만료로 주문 취소 + 결제 역보상 요청: orderId=${event.orderId}")
    }

    private fun reconstructReservationIds(orderId: Long): List<String>? {
        val orderItems = orderItemRepository.findByOrderId(orderId)
        if (orderItems.isEmpty()) return null
        return orderItems.mapNotNull { it.reservationId }
    }

    @KafkaListener(topics = ["payment-compensation"], groupId = "order-saga-service")
    fun handlePaymentCompensation(message: String) {
        val node = objectMapper.readTree(message)
        val eventId = node.get("eventId")?.asText() ?: return

        val existingLog = sagaEventLogRepository.findByEventId(eventId)
        if (existingLog != null && existingLog.isFullyCompleted()) {
            logger.info("이미 처리된 보상 이벤트: $eventId")
            return
        }

        val orderId = node.get("orderId")?.asLong() ?: return

        val reservationIds = if (existingLog == null || !existingLog.isStepCompleted(SagaEventLog.LOCAL_COMPLETED)) {
            executeCompensationLocalPhase(eventId, orderId)
        } else {
            reconstructReservationIds(orderId)
        }

        if (reservationIds == null) return

        executeCompensationRemotePhase(eventId, orderId, reservationIds)
    }

    private fun executeCompensationLocalPhase(eventId: String, orderId: Long): List<String>? {
        return transactionTemplate.execute {
            val order = orderRepository.findById(orderId).orElse(null)
            if (order == null) {
                logger.error("주문을 찾을 수 없음: orderId=$orderId")
                return@execute null
            }

            if (order.isCancellable()) {
                order.updateStatus(OrderStatus.CANCELLED)
                orderRepository.save(order)
            }

            val log = SagaEventLog(eventId = eventId, eventType = "PAYMENT_COMPENSATION", orderId = orderId)
            log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            sagaEventLogRepository.save(log)

            orderItemRepository.findByOrderId(orderId).mapNotNull { it.reservationId }
        }
    }

    private fun executeCompensationRemotePhase(eventId: String, orderId: Long, reservationIds: List<String>) {
        try {
            reservationIds.forEach { inventoryCommandPort.cancelReservation(it) }
        } catch (e: Exception) {
            logger.error("보상 예약 취소 실패: orderId=$orderId", e)
        }

        transactionTemplate.execute {
            val log = sagaEventLogRepository.findByEventId(eventId)!!
            log.markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
            sagaEventLogRepository.save(log)
        }

        logger.info("결제 보상 처리 완료 (예약 취소 + 주문 CANCELLED): orderId=$orderId")
    }
}
