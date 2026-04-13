package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.consumer.executeIdempotently
import com.hoppingmall.product.product.domain.StatisticsEventLog
import com.hoppingmall.product.product.domain.repository.StatisticsEventLogRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.dto.PaymentCancelledEvent
import com.hoppingmall.product.statistics.dto.PaymentCompletedEvent
import com.hoppingmall.product.statistics.port.OrderItemQueryPort
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class StatisticsEventConsumer(
    private val statisticsEventLogRepository: StatisticsEventLogRepository,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val productStatisticsCommandService: ProductStatisticsCommandService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(StatisticsEventConsumer::class.java)

    @KafkaListener(topics = [KafkaTopics.PAYMENT], groupId = "statistics-service")
    fun handlePaymentCompleted(message: String) {
        val event = objectMapper.readValue(message, PaymentCompletedEvent::class.java)

        if (statisticsEventLogRepository.existsByEventId(event.transactionId)) return

        val orderItems = orderItemQueryPort.findByOrderId(event.orderId)

        executeIdempotently(
            eventId = event.transactionId,
            eventDescription = "통계",
            logger = logger,
            existsCheck = { statisticsEventLogRepository.existsByEventId(event.transactionId) }
        ) {
            orderItems.forEach { item ->
                productStatisticsCommandService.incrementSalesStats(
                    item.productId,
                    item.quantity.toLong(),
                    item.totalPrice
                )
            }

            statisticsEventLogRepository.save(
                StatisticsEventLog(
                    eventId = event.transactionId,
                    eventType = "PaymentCompleted",
                    orderId = event.orderId
                )
            )

            logger.info("결제 완료 통계 반영: orderId=${event.orderId}")
        }
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_COMPENSATION], groupId = "statistics-compensation-service")
    fun handleCompensationEvent(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != "PaymentCancelled") return

        val event = objectMapper.treeToValue(node, PaymentCancelledEvent::class.java)
        handlePaymentCancelled(event)
    }

    private fun handlePaymentCancelled(event: PaymentCancelledEvent) {
        if (statisticsEventLogRepository.existsByEventId(event.eventId)) return

        val orderItems = orderItemQueryPort.findByOrderId(event.orderId)

        executeIdempotently(
            eventId = event.eventId,
            eventDescription = "통계 보상",
            logger = logger,
            existsCheck = { statisticsEventLogRepository.existsByEventId(event.eventId) }
        ) {
            orderItems.forEach { item ->
                productStatisticsCommandService.decrementSalesStats(
                    item.productId,
                    item.quantity.toLong(),
                    item.totalPrice
                )
            }

            statisticsEventLogRepository.save(
                StatisticsEventLog(
                    eventId = event.eventId,
                    eventType = "PaymentCancelled",
                    orderId = event.orderId
                )
            )

            logger.info("결제 취소 통계 반영: orderId=${event.orderId}")
        }
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_REVERSAL], groupId = "statistics-compensation-service")
    fun handlePaymentReversal(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != "PaymentReversalRequested") return

        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return

        if (statisticsEventLogRepository.existsByEventId(eventId)) return

        val orderItems = orderItemQueryPort.findByOrderId(orderId)

        executeIdempotently(
            eventId = eventId,
            eventDescription = "통계 역보상",
            logger = logger,
            existsCheck = { statisticsEventLogRepository.existsByEventId(eventId) }
        ) {
            orderItems.forEach { item ->
                productStatisticsCommandService.decrementSalesStats(
                    item.productId,
                    item.quantity.toLong(),
                    item.totalPrice
                )
            }

            statisticsEventLogRepository.save(
                StatisticsEventLog(
                    eventId = eventId,
                    eventType = "PaymentReversalRequested",
                    orderId = orderId
                )
            )

            logger.info("결제 역보상 통계 반영: orderId=$orderId")
        }
    }
}
