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

@Service
class OrderSagaConsumer(
    private val sagaEventLogRepository: SagaEventLogRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val inventoryCommandPort: InventoryCommandPort,
    private val transactionalEventPublisherPort: TransactionalEventPublisherPort,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OrderSagaConsumer::class.java)

    @KafkaListener(topics = ["payment"], groupId = "order-saga-service")
    @Transactional
    fun handlePaymentCompleted(message: String) {
        val event = objectMapper.readValue(message, PaymentCompletedEvent::class.java)
        val eventId = "payment-completed-${event.transactionId}"

        if (sagaEventLogRepository.existsByEventId(eventId)) {
            logger.info("이미 처리된 결제 완료 이벤트: $eventId")
            return
        }

        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) {
            logger.error("주문을 찾을 수 없음: orderId=${event.orderId}")
            return
        }

        if (order.status != OrderStatus.CREATED) {
            logger.warn("주문 상태가 CREATED가 아님: orderId=${event.orderId}, status=${order.status}")
            sagaEventLogRepository.save(SagaEventLog(eventId = eventId, eventType = "PAYMENT_COMPLETED", orderId = event.orderId))
            return
        }

        val orderItems = orderItemRepository.findByOrderId(event.orderId)
        val reservationIds = orderItems.mapNotNull { it.reservationId }

        val allConfirmed = if (reservationIds.isNotEmpty()) {
            inventoryCommandPort.confirmReservations(reservationIds)
        } else {
            true
        }

        if (allConfirmed) {
            order.updateStatus(OrderStatus.PAID)
            orderRepository.save(order)
            logger.info("주문 결제 확정: orderId=${event.orderId}")
        } else {
            order.updateStatus(OrderStatus.CANCELLED)
            orderRepository.save(order)

            reservationIds.forEach { inventoryCommandPort.cancelReservation(it) }

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
            logger.warn("예약 만료로 주문 취소 + 결제 역보상 요청: orderId=${event.orderId}")
        }

        sagaEventLogRepository.save(SagaEventLog(eventId = eventId, eventType = "PAYMENT_COMPLETED", orderId = event.orderId))
    }

    @KafkaListener(topics = ["payment-compensation"], groupId = "order-saga-service")
    @Transactional
    fun handlePaymentCompensation(message: String) {
        val node = objectMapper.readTree(message)
        val eventId = node.get("eventId")?.asText() ?: return

        if (sagaEventLogRepository.existsByEventId(eventId)) {
            logger.info("이미 처리된 보상 이벤트: $eventId")
            return
        }

        val orderId = node.get("orderId")?.asLong() ?: return

        val order = orderRepository.findById(orderId).orElse(null)
        if (order == null) {
            logger.error("주문을 찾을 수 없음: orderId=$orderId")
            return
        }

        if (order.isCancellable()) {
            order.updateStatus(OrderStatus.CANCELLED)
            orderRepository.save(order)
        }

        val orderItems = orderItemRepository.findByOrderId(orderId)
        val reservationIds = orderItems.mapNotNull { it.reservationId }
        reservationIds.forEach { inventoryCommandPort.cancelReservation(it) }

        sagaEventLogRepository.save(SagaEventLog(eventId = eventId, eventType = "PAYMENT_COMPENSATION", orderId = orderId))
        logger.info("결제 보상 처리 완료 (예약 취소 + 주문 CANCELLED): orderId=$orderId")
    }
}
