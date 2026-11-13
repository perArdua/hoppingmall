package com.hoppingmall.order.order.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

@Service
class SagaTimeoutScheduler(
    private val sagaEventLogRepository: SagaEventLogRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val inventoryCommandPort: InventoryCommandPort,
    private val paymentQueryPort: PaymentQueryPort,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val sagaCompensationMetrics: SagaCompensationMetrics
) {
    private val logger = LoggerFactory.getLogger(SagaTimeoutScheduler::class.java)

    @Value("\${saga.timeout-ms:300000}")
    private var sagaTimeoutMs: Long = 300_000

    @Scheduled(fixedDelayString = "\${saga.timeout-scan-interval-ms:60000}")
    fun checkTimedOutSagas() {
        val timedOutSagas = sagaEventLogRepository.findTimedOutSagas(
            LocalDateTime.now().minus(Duration.ofMillis(sagaTimeoutMs))
        )
        if (timedOutSagas.isEmpty()) return

        logger.warn("타임아웃된 saga {} 건 감지", timedOutSagas.size)

        timedOutSagas.forEach { saga ->
            try {
                compensateTimedOutSaga(saga)
            } catch (e: Exception) {
                logger.error("saga 타임아웃 보상 실패: sagaId={}, orderId={}", saga.id, saga.orderId, e)
                sagaCompensationMetrics.recordFailed()
            }
        }
    }

    private fun compensateTimedOutSaga(timedOutSaga: SagaEventLog) {
        val sagaId = timedOutSaga.id!!
        val orderId = timedOutSaga.orderId
        val eventId = timedOutSaga.eventId
        val claimed = transactionTemplate.execute { claimTimedOutSaga(sagaId, orderId) } ?: false
        if (!claimed) return

        val payment = resolveReversalPayment(timedOutSaga)
        restoreReservations(orderId)
        val compensated = transactionTemplate.execute {
            orderRepository.lockById(orderId)
            val saga = sagaEventLogRepository.findByIdOrNull(sagaId) ?: return@execute false
            if (saga.isTimedOut()) return@execute false

            saga.markAsTimedOut()
            sagaEventLogRepository.save(saga)

            val order = orderRepository.findByIdOrNull(orderId) ?: return@execute false
            if (order.status != OrderStatus.PAID || !order.isCancellable()) {
                logger.warn("타임아웃 보상 중 주문 상태가 바뀌어 역보상 생략: sagaId={}, orderId={}, status={}", sagaId, orderId, order.status)
                return@execute false
            }
            order.updateStatus(OrderStatus.CANCEL_REQUESTED)
            order.updateStatus(OrderStatus.CANCELLED)
            orderRepository.save(order)

            transactionalEventPublisher.publishEvent(
                aggregateType = "Order",
                aggregateId = orderId.toString(),
                eventType = "PaymentReversalRequested",
                eventData = mapOf(
                    "eventType" to "PaymentReversalRequested",
                    "eventId" to "timeout-$eventId",
                    "orderId" to orderId,
                    "paymentId" to payment.paymentId,
                    "amount" to payment.amount.toPlainString(),
                    "userId" to order.buyerId,
                    "reason" to "SAGA_TIMEOUT"
                ),
                topic = KafkaTopics.PAYMENT_REVERSAL,
                partitionKey = orderId.toString()
            )
            true
        } ?: false

        if (compensated) {
            sagaCompensationMetrics.recordTimeoutTriggered()
            logger.warn("saga 타임아웃 보상 완료: orderId={}, eventId={}", orderId, eventId)
        }
    }

    private fun resolveReversalPayment(saga: SagaEventLog): ReversalPayment {
        val paymentId = saga.paymentId?.takeIf { it > 0 }
        val amount = saga.paymentAmount
        if (paymentId != null && amount != null) return ReversalPayment(paymentId, amount)

        val payment = paymentQueryPort.findByOrderIdOrThrowIfUnavailable(saga.orderId)
        if (payment == null) {
            val reversal = ReversalPayment(paymentId ?: UNKNOWN_PAYMENT_ID, amount ?: BigDecimal.ZERO)
            logger.warn(
                "타임아웃 saga의 결제를 조회로 찾지 못해 아는 값으로 역보상 요청(결제 ID 0이면 payment가 주문 ID로 찾음): sagaId={}, orderId={}, paymentId={}, amount={}",
                saga.id, saga.orderId, reversal.paymentId, reversal.amount
            )
            return reversal
        }
        logger.info("타임아웃 saga에 결제 정보가 없어 주문 ID로 조회해 채움: sagaId={}, orderId={}, paymentId={}", saga.id, saga.orderId, payment.id)
        return ReversalPayment(payment.id, payment.amount)
    }

    private fun claimTimedOutSaga(sagaId: Long, orderId: Long): Boolean {
        orderRepository.lockById(orderId)
        val saga = sagaEventLogRepository.findByIdOrNull(sagaId) ?: return false
        if (saga.isTimedOut() || saga.isFullyCompleted()) return false
        if (saga.isTimeoutClaimed()) return true

        val order = orderRepository.findByIdOrNull(orderId)
        if (order == null || order.status != OrderStatus.PAID || !order.isCancellable()) {
            saga.markAsTimedOut()
            sagaEventLogRepository.save(saga)
            logger.warn("결제 완료 상태가 아닌 주문의 타임아웃 saga, 보상 생략: sagaId={}, orderId={}, status={}", sagaId, orderId, order?.status)
            return false
        }

        saga.claimTimeout()
        sagaEventLogRepository.save(saga)
        return true
    }

    private fun restoreReservations(orderId: Long) {
        val reservationIds = orderItemRepository.findByOrderId(orderId).mapNotNull { it.reservationId }.distinct()
        if (reservationIds.isEmpty()) return
        inventoryCommandPort.cancelReservations(reservationIds)
        inventoryCommandPort.cancelConfirmedReservations(reservationIds)
    }

    private data class ReversalPayment(val paymentId: Long, val amount: BigDecimal)

    companion object {
        private const val UNKNOWN_PAYMENT_ID = 0L
    }
}
