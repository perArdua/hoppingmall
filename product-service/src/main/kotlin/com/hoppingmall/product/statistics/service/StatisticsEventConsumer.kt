package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.consumer.executeIdempotently
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.product.product.domain.StatisticsEventLog
import com.hoppingmall.product.product.domain.repository.StatisticsEventLogRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.dto.PaymentCancelledEvent
import com.hoppingmall.product.statistics.dto.PaymentCompletedEvent
import com.hoppingmall.product.statistics.port.OrderItemQueryPort
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class StatisticsEventConsumer(
    private val statisticsEventLogRepository: StatisticsEventLogRepository,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val productStatisticsCommandService: ProductStatisticsCommandService,
    private val objectMapper: ObjectMapper,
    private val kafkaEventMapper: KafkaEventMapper,
    private val transactionTemplate: TransactionTemplate
) {

    private val logger = LoggerFactory.getLogger(StatisticsEventConsumer::class.java)

    @KafkaListener(topics = [KafkaTopics.PAYMENT], groupId = "statistics-service")
    fun handlePaymentCompleted(message: String) {
        val event = kafkaEventMapper.readValue(message, PaymentCompletedEvent::class.java)

        if (statisticsEventLogRepository.existsByEventId(event.transactionId)) return

        if (statisticsEventLogRepository.existsByEventId(cancelledEventId(event.paymentId)) ||
            statisticsEventLogRepository.existsByOrderIdAndEventType(event.orderId, REVERSAL_EVENT_TYPE)
        ) {
            executeIdempotently(
                eventId = event.transactionId,
                eventDescription = "통계",
                logger = logger,
                existsCheck = { statisticsEventLogRepository.existsByEventId(event.transactionId) }
            ) {
                statisticsEventLogRepository.save(
                    StatisticsEventLog(
                        eventId = event.transactionId,
                        eventType = COMPLETED_EVENT_TYPE,
                        orderId = event.orderId
                    )
                )
                logger.info("이미 취소·역보상된 결제의 완료 이벤트, 통계에 더하지 않음: orderId=${event.orderId}")
            }
            return
        }

        val orderItems = orderItemQueryPort.findByOrderId(event.orderId)

        executeIdempotently(
            eventId = event.transactionId,
            eventDescription = "통계",
            logger = logger,
            existsCheck = { statisticsEventLogRepository.existsByEventId(event.transactionId) }
        ) {
            transactionTemplate.executeWithoutResult {
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
                        eventType = COMPLETED_EVENT_TYPE,
                        orderId = event.orderId
                    )
                )
            }

            logger.info("결제 완료 통계 반영: orderId=${event.orderId}")
        }
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_COMPENSATION], groupId = "statistics-compensation-service")
    fun handleCompensationEvent(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != "PaymentCancelled") return

        val event = kafkaEventMapper.treeToValue(node, PaymentCancelledEvent::class.java)
        handlePaymentCancelled(event)
    }

    private fun handlePaymentCancelled(event: PaymentCancelledEvent) {
        if (statisticsEventLogRepository.existsByEventId(event.eventId)) return

        if (event.transactionId.isNotBlank() && !statisticsEventLogRepository.existsByEventId(event.transactionId)) {
            throw IllegalStateException("결제 완료 통계가 아직 반영되지 않아 취소를 다시 시도: orderId=${event.orderId}")
        }

        val orderItems = orderItemQueryPort.findByOrderId(event.orderId)

        executeIdempotently(
            eventId = event.eventId,
            eventDescription = "통계 보상",
            logger = logger,
            existsCheck = { statisticsEventLogRepository.existsByEventId(event.eventId) }
        ) {
            transactionTemplate.executeWithoutResult {
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
            }

            logger.info("결제 취소 통계 반영: orderId=${event.orderId}")
        }
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_REVERSAL], groupId = "statistics-compensation-service")
    fun handlePaymentReversal(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != REVERSAL_EVENT_TYPE) return

        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return

        if (statisticsEventLogRepository.existsByEventId(eventId)) return

        if (!statisticsEventLogRepository.existsByOrderIdAndEventType(orderId, COMPLETED_EVENT_TYPE)) {
            throw IllegalStateException("결제 완료 통계가 아직 반영되지 않아 역보상을 다시 시도: orderId=$orderId")
        }

        val orderItems = orderItemQueryPort.findByOrderId(orderId)

        executeIdempotently(
            eventId = eventId,
            eventDescription = "통계 역보상",
            logger = logger,
            existsCheck = { statisticsEventLogRepository.existsByEventId(eventId) }
        ) {
            transactionTemplate.executeWithoutResult {
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
                        eventType = REVERSAL_EVENT_TYPE,
                        orderId = orderId
                    )
                )
            }

            logger.info("결제 역보상 통계 반영: orderId=$orderId")
        }
    }

    private fun cancelledEventId(paymentId: Long) = "payment-cancelled-$paymentId"

    companion object {
        private const val COMPLETED_EVENT_TYPE = "PaymentCompleted"
        private const val REVERSAL_EVENT_TYPE = "PaymentReversalRequested"
    }
}
