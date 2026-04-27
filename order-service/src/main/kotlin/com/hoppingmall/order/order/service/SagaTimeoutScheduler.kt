package com.hoppingmall.order.order.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class SagaTimeoutScheduler(
    private val sagaEventLogRepository: SagaEventLogRepository,
    private val orderRepository: OrderRepository,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(SagaTimeoutScheduler::class.java)

    @Scheduled(fixedDelay = 60_000)
    fun checkTimedOutSagas() {
        val timedOutSagas = sagaEventLogRepository.findTimedOutSagas(LocalDateTime.now())
        if (timedOutSagas.isEmpty()) return

        logger.warn("타임아웃된 saga {} 건 감지", timedOutSagas.size)

        timedOutSagas.forEach { saga ->
            try {
                compensateTimedOutSaga(saga.id!!, saga.orderId, saga.eventId)
            } catch (e: Exception) {
                logger.error("saga 타임아웃 보상 실패: sagaId={}, orderId={}", saga.id, saga.orderId, e)
            }
        }
    }

    private fun compensateTimedOutSaga(sagaId: Long, orderId: Long, eventId: String) {
        val compensated = transactionTemplate.execute {
            val saga = sagaEventLogRepository.findByIdOrNull(sagaId) ?: return@execute false
            if (saga.isTimedOut() || saga.isFullyCompleted()) return@execute false

            saga.markAsTimedOut()
            sagaEventLogRepository.save(saga)

            val order = orderRepository.findByIdOrNull(orderId) ?: return@execute false
            if (order.status == OrderStatus.PAID && order.isCancellable()) {
                order.updateStatus(OrderStatus.CANCEL_REQUESTED)
                order.updateStatus(OrderStatus.CANCELLED)
                orderRepository.save(order)
            }

            transactionalEventPublisher.publishEvent(
                aggregateType = "Order",
                aggregateId = orderId.toString(),
                eventType = "PaymentReversalRequested",
                eventData = mapOf(
                    "eventType" to "PaymentReversalRequested",
                    "eventId" to "timeout-$eventId",
                    "orderId" to orderId,
                    "reason" to "SAGA_TIMEOUT"
                ),
                topic = KafkaTopics.PAYMENT_REVERSAL,
                partitionKey = orderId.toString()
            )
            true
        } ?: false

        if (compensated) {
            logger.warn("saga 타임아웃 보상 완료: orderId={}, eventId={}", orderId, eventId)
        }
    }
}
