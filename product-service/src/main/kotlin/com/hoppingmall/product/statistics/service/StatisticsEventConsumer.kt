package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.consumer.executeIdempotently
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.product.config.ProductConsumerGroups
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

    @KafkaListener(topics = [KafkaTopics.PAYMENT], groupId = ProductConsumerGroups.STATISTICS)
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
            var voided = false
            transactionTemplate.executeWithoutResult {
                voided = statisticsEventLogRepository
                    .lockIdsByOrderIdAndEventTypeIn(event.orderId, VOIDING_EVENT_TYPES)
                    .isNotEmpty()
                if (!voided) {
                    orderItems.forEach { item ->
                        productStatisticsCommandService.incrementSalesStats(
                            item.productId,
                            item.quantity.toLong(),
                            item.totalPrice
                        )
                    }
                }

                statisticsEventLogRepository.save(
                    StatisticsEventLog(
                        eventId = event.transactionId,
                        eventType = COMPLETED_EVENT_TYPE,
                        orderId = event.orderId
                    )
                )
            }

            if (!voided) {
                logger.info("결제 완료 통계 반영: orderId=${event.orderId}")
            } else {
                logger.info("이미 취소·역보상된 결제의 완료 이벤트, 통계에 더하지 않음: orderId=${event.orderId}")
            }
        }
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_COMPENSATION], groupId = ProductConsumerGroups.STATISTICS_COMPENSATION)
    fun handleCompensationEvent(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != CANCELLED_EVENT_TYPE) return

        val event = kafkaEventMapper.treeToValue(node, PaymentCancelledEvent::class.java)
        handlePaymentCancelled(event)
    }

    private fun handlePaymentCancelled(event: PaymentCancelledEvent) {
        if (statisticsEventLogRepository.existsByEventId(event.eventId)) return

        if (event.transactionId.isNotBlank() &&
            !statisticsEventLogRepository.existsByEventId(event.transactionId) &&
            recordBeforeSales(event.eventId, CANCELLED_EVENT_TYPE, event.orderId, "통계 보상")
        ) {
            return
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
                        eventType = CANCELLED_EVENT_TYPE,
                        orderId = event.orderId
                    )
                )
            }

            logger.info("결제 취소 통계 반영: orderId=${event.orderId}")
        }
    }

    @KafkaListener(topics = [KafkaTopics.PAYMENT_REVERSAL], groupId = ProductConsumerGroups.STATISTICS_COMPENSATION)
    fun handlePaymentReversal(message: String) {
        val node = objectMapper.readTree(message)
        val eventType = node.get("eventType")?.asText()

        if (eventType != REVERSAL_EVENT_TYPE) return

        val eventId = node.get("eventId")?.asText() ?: return
        val orderId = node.get("orderId")?.asLong() ?: return

        if (statisticsEventLogRepository.existsByEventId(eventId)) return

        if (!statisticsEventLogRepository.existsByOrderIdAndEventType(orderId, COMPLETED_EVENT_TYPE) &&
            recordBeforeSales(eventId, REVERSAL_EVENT_TYPE, orderId, "통계 역보상")
        ) {
            return
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

    private fun recordBeforeSales(eventId: String, eventType: String, orderId: Long, eventDescription: String): Boolean {
        var recorded = false
        executeIdempotently(
            eventId = eventId,
            eventDescription = eventDescription,
            logger = logger,
            existsCheck = { statisticsEventLogRepository.existsByEventId(eventId) }
        ) {
            transactionTemplate.executeWithoutResult {
                val salesReflected = statisticsEventLogRepository
                    .lockIdsByOrderIdAndEventTypeIn(orderId, listOf(COMPLETED_EVENT_TYPE))
                    .isNotEmpty()
                if (!salesReflected) {
                    statisticsEventLogRepository.save(
                        StatisticsEventLog(eventId = eventId, eventType = eventType, orderId = orderId)
                    )
                    recorded = true
                }
            }
        }

        if (recorded) {
            logger.info("판매 반영 전에 온 {}, 판매를 빼지 않고 기록만 남김: orderId={}, eventId={}", eventDescription, orderId, eventId)
            return true
        }
        return statisticsEventLogRepository.existsByEventId(eventId)
    }

    private fun cancelledEventId(paymentId: Long) = "payment-cancelled-$paymentId"

    companion object {
        private const val COMPLETED_EVENT_TYPE = "PaymentCompleted"
        private const val CANCELLED_EVENT_TYPE = "PaymentCancelled"
        private const val REVERSAL_EVENT_TYPE = "PaymentReversalRequested"
        private val VOIDING_EVENT_TYPES = listOf(CANCELLED_EVENT_TYPE, REVERSAL_EVENT_TYPE)
    }
}
