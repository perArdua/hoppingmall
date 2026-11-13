package com.hoppingmall.order.order.service

import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("saga 타임아웃 스케줄러 대상 선택 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SagaTimeoutSchedulerMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val inventoryCommandPort: InventoryCommandPort = mock()

    private val paymentQueryPort: PaymentQueryPort = mock()

    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private val sagaCompensationMetrics: SagaCompensationMetrics = mock()

    private lateinit var scheduler: SagaTimeoutScheduler

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
        scheduler = SagaTimeoutScheduler(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventoryCommandPort,
            paymentQueryPort,
            transactionalEventPublisher,
            TransactionTemplate(transactionManager),
            sagaCompensationMetrics
        )
    }

    @Test
    fun 재고_되돌리기가_끝나지_않은_결제_취소_완료_로그는_타임아웃_대상이_아니다() {
        val orderId = saveOrder(OrderStatus.CANCELLED)
        saveLocalOnlySaga("cancel-completed-timeout-1", "PAYMENT_CANCELLATION_COMPLETED", orderId, paymentId = null)

        scheduler.checkTimedOutSagas()

        verifyReversalPublished(count = 0)
        verify(sagaCompensationMetrics, never()).recordTimeoutTriggered()
        assertThat(timedOut("cancel-completed-timeout-1")).isFalse()
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
    }

    @Test
    fun 예약_취소가_끝나지_않은_결제_보상_로그는_타임아웃_대상이_아니다() {
        val orderId = saveOrder(OrderStatus.CANCELLED)
        saveLocalOnlySaga("payment-compensation-timeout-1", "PAYMENT_COMPENSATION", orderId, paymentId = null)

        scheduler.checkTimedOutSagas()

        verifyReversalPublished(count = 0)
        verify(sagaCompensationMetrics, never()).recordTimeoutTriggered()
        assertThat(timedOut("payment-compensation-timeout-1")).isFalse()
    }

    @Test
    fun 재고_확정이_끝나지_않은_결제_완료_로그는_타임아웃으로_보고_역보상을_낸다() {
        val orderId = saveOrder(OrderStatus.PAID)
        saveLocalOnlySaga("payment-completed-timeout-1", "PAYMENT_COMPLETED", orderId, paymentId = PAYMENT_ID)

        scheduler.checkTimedOutSagas()

        verifyReversalPublished(count = 1)
        verify(sagaCompensationMetrics).recordTimeoutTriggered()
        assertThat(timedOut("payment-completed-timeout-1")).isTrue()
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED.name)
    }

    private fun saveOrder(status: OrderStatus): Long {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        if (status == OrderStatus.CANCELLED) {
            order.updateStatus(OrderStatus.CANCEL_REQUESTED)
            order.updateStatus(OrderStatus.CANCELLED)
        }
        return orderRepository.save(order).id!!
    }

    private fun saveLocalOnlySaga(eventId: String, eventType: String, orderId: Long, paymentId: Long?) {
        val createdAt = LocalDateTime.now().minusMinutes(10)
        val log = SagaEventLog(
            eventId = eventId,
            eventType = eventType,
            orderId = orderId,
            paymentId = paymentId,
            createdAt = createdAt,
            timeoutAt = createdAt.plusMinutes(SagaEventLog.DEFAULT_TIMEOUT_MINUTES)
        )
        log.markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        sagaEventLogRepository.save(log)
    }

    private fun verifyReversalPublished(count: Int) {
        verify(transactionalEventPublisher, times(count)).publishEvent(
            any(), any(), eq("PaymentReversalRequested"), any(), any(), any()
        )
    }

    private fun timedOut(eventId: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT timed_out FROM saga_event_logs WHERE event_id = ?", Boolean::class.java, eventId
        )!!

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    companion object {
        private const val BUYER_ID = 10L
        private const val PAYMENT_ID = 100L
    }
}
