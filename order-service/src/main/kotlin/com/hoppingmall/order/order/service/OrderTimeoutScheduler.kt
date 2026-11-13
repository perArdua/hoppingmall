package com.hoppingmall.order.order.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class OrderTimeoutScheduler(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderCommandService: OrderCommandService,
    private val paymentQueryPort: PaymentQueryPort,
    private val inventoryCommandPort: InventoryCommandPort,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(OrderTimeoutScheduler::class.java)

    @Value("\${order.timeout.paying-minutes:15}")
    private var payingTimeoutMinutes: Long = 15

    @Value("\${order.timeout.cancel-requested-minutes:5}")
    private var cancelRequestedTimeoutMinutes: Long = 5

    @Value("\${order.timeout.cancel-max-retries:3}")
    private var cancelMaxRetries: Int = 3

    @Value("\${order.timeout.batch-size:100}")
    private var batchSize: Int = 100

    @Scheduled(fixedDelayString = "\${order.timeout.scan-interval-ms:60000}")
    fun checkTimedOutOrders() {
        findStale(OrderStatus.PAYING, payingTimeoutMinutes).forEach { order ->
            runCatching { closeStalePayingOrder(order.id!!) }
                .onFailure { logger.warn("결제 대기 시간 초과 주문 처리 실패: orderId={}", order.id, it) }
        }
        findStale(OrderStatus.CANCEL_REQUESTED, cancelRequestedTimeoutMinutes).forEach { order ->
            runCatching { resolveStaleCancelRequest(order.id!!, order.cancelRetryCount) }
                .onFailure { logger.warn("취소 요청 시간 초과 주문 처리 실패: orderId={}", order.id, it) }
        }
    }

    private fun findStale(status: OrderStatus, minutes: Long): List<Order> =
        orderRepository.findByStatusAndUpdatedAtBefore(
            status,
            LocalDateTime.now().minusMinutes(minutes),
            PageRequest.of(0, batchSize, Sort.by("updatedAt"))
        )

    private fun closeStalePayingOrder(orderId: Long) {
        val payment = paymentQueryPort.findByOrderIdOrThrowIfUnavailable(orderId)
        if (payment?.isSuccess() == true) {
            logger.info("결제 대기 시간 초과 주문의 결제가 SUCCESS, 결제 완료 이벤트를 기다림: orderId={}", orderId)
            return
        }
        if (orderCommandService.cancelPayingOrder(orderId)) {
            logger.warn("결제 대기 시간 초과로 주문 취소: orderId={}, paymentStatus={}", orderId, payment?.status)
        }
    }

    private fun resolveStaleCancelRequest(orderId: Long, observedRetryCount: Int) {
        val payment = paymentQueryPort.findByOrderIdOrThrowIfUnavailable(orderId)
        transactionTemplate.execute {
            orderRepository.lockById(orderId)
            val order = orderRepository.findByIdOrNull(orderId) ?: return@execute
            if (order.status != OrderStatus.CANCEL_REQUESTED || order.cancelRetryCount != observedRetryCount) {
                return@execute
            }
            when {
                payment?.status == PAYMENT_CANCELLED -> {
                    order.updateStatus(OrderStatus.CANCELLED)
                    restoreConfirmedInventory(orderId)
                    logger.warn("취소 요청 시간 초과 주문의 결제가 이미 취소됨, 주문 취소: orderId={}", orderId)
                }
                order.cancelRetryCount >= cancelMaxRetries -> {
                    order.updateStatus(OrderStatus.CANCEL_FAILED)
                    logger.warn("결제 취소 재요청 {}번 뒤에도 끝나지 않아 CANCEL_FAILED: orderId={}, paymentStatus={}", order.cancelRetryCount, orderId, payment?.status)
                }
                else -> {
                    order.recordCancellationRetry()
                    requestPaymentCancellationAgain(orderId, order.cancelRetryCount)
                }
            }
        }
    }

    private fun restoreConfirmedInventory(orderId: Long) {
        val orderItems = orderItemRepository.findByOrderId(orderId)
        val reservationIds = orderItems.mapNotNull { it.reservationId }
        if (reservationIds.isNotEmpty()) {
            inventoryCommandPort.cancelConfirmedReservations(reservationIds)
        } else {
            orderItems.forEach { inventoryCommandPort.increaseStock(it.productId, it.quantity) }
        }
    }

    private fun requestPaymentCancellationAgain(orderId: Long, retry: Int) {
        transactionalEventPublisher.publishEvent(
            aggregateType = "Order",
            aggregateId = orderId.toString(),
            eventType = "PaymentCancellationRequested",
            eventData = mapOf(
                "eventType" to "PaymentCancellationRequested",
                "eventId" to "cancel-retry-$orderId-$retry-${System.currentTimeMillis()}",
                "orderId" to orderId
            ),
            topic = KafkaTopics.PAYMENT_COMPENSATION,
            partitionKey = orderId.toString()
        )
        logger.warn("취소 요청 시간 초과, 결제 취소 재요청: orderId={}, retry={}", orderId, retry)
    }

    companion object {
        private const val PAYMENT_CANCELLED = "CANCELLED"
    }
}
