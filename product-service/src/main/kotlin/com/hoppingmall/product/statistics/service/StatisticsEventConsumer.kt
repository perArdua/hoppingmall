package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.product.product.domain.StatisticsEventLog
import com.hoppingmall.product.product.domain.repository.StatisticsEventLogRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.dto.PaymentCancelledEvent
import com.hoppingmall.product.statistics.dto.PaymentCompletedEvent
import com.hoppingmall.product.statistics.port.OrderItemQueryPort
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StatisticsEventConsumer(
    private val statisticsEventLogRepository: StatisticsEventLogRepository,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val productStatisticsCommandService: ProductStatisticsCommandService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(StatisticsEventConsumer::class.java)

    @KafkaListener(topics = ["payment"], groupId = "statistics-service")
    @Transactional
    fun handlePaymentCompleted(message: String) {
        val event = objectMapper.readValue(message, PaymentCompletedEvent::class.java)
        try {
            if (statisticsEventLogRepository.existsByEventId(event.transactionId)) {
                logger.info("이미 처리된 통계 이벤트: ${event.transactionId}")
                return
            }

            val orderItems = orderItemQueryPort.findByOrderId(event.orderId)
            orderItems.forEach { item ->
                productStatisticsCommandService.incrementSalesStats(
                    item.productId,
                    item.quantity.toLong(),
                    item.totalPrice
                )
            }

            try {
                statisticsEventLogRepository.save(
                    StatisticsEventLog(
                        eventId = event.transactionId,
                        eventType = "PaymentCompleted",
                        orderId = event.orderId
                    )
                )
            } catch (e: DataIntegrityViolationException) {
                logger.info("이미 처리된 통계 이벤트: ${event.transactionId}")
                return
            }

            logger.info("결제 완료 통계 반영: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 완료 통계 처리 실패: ${event.transactionId}, 오류: ${e.message}")
            throw e
        }
    }

    @KafkaListener(topics = ["payment-compensation"], groupId = "statistics-compensation-service")
    @Transactional
    fun handleCompensationEvent(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != "PaymentCancelled") return

        val event = objectMapper.treeToValue(node, PaymentCancelledEvent::class.java)
        handlePaymentCancelled(event)
    }

    private fun handlePaymentCancelled(event: PaymentCancelledEvent) {
        try {
            if (statisticsEventLogRepository.existsByEventId(event.eventId)) {
                logger.info("이미 처리된 통계 보상 이벤트: ${event.eventId}")
                return
            }

            val orderItems = orderItemQueryPort.findByOrderId(event.orderId)
            orderItems.forEach { item ->
                productStatisticsCommandService.decrementSalesStats(
                    item.productId,
                    item.quantity.toLong(),
                    item.totalPrice
                )
            }

            try {
                statisticsEventLogRepository.save(
                    StatisticsEventLog(
                        eventId = event.eventId,
                        eventType = "PaymentCancelled",
                        orderId = event.orderId
                    )
                )
            } catch (e: DataIntegrityViolationException) {
                logger.info("이미 처리된 통계 보상 이벤트: ${event.eventId}")
                return
            }

            logger.info("결제 취소 통계 반영: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 취소 통계 처리 실패: ${event.eventId}, 오류: ${e.message}")
            throw e
        }
    }
}
