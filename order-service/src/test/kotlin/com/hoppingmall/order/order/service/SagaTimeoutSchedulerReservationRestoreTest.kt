package com.hoppingmall.order.order.service

import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.ResourceAccessException
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("saga 타임아웃 보상의 예약·확정 재고 복원")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class SagaTimeoutSchedulerReservationRestoreTest {

    @Mock
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    private lateinit var scheduler: SagaTimeoutScheduler

    private lateinit var saga: SagaEventLog

    private lateinit var order: Order

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        scheduler = SagaTimeoutScheduler(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventoryCommandPort,
            transactionalEventPublisher,
            transactionTemplate,
            sagaCompensationMetrics
        )
        saga = SagaEventLog(
            eventId = "payment-completed-txn-746",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L,
            paymentId = 100L,
            timeoutAt = LocalDateTime.now().minusMinutes(1)
        ).apply {
            id = 100L
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        }
        order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )

        whenever(sagaEventLogRepository.findTimedOutSagas(any())).thenReturn(listOf(saga))
        whenever(sagaEventLogRepository.findById(100L)).thenReturn(Optional.of(saga))
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 타임아웃으로_주문을_취소할_때_예약과_확정된_재고를_차례로_복원한다() {
        scheduler.checkTimedOutSagas()

        inOrder(inventoryCommandPort) {
            verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
            verify(inventoryCommandPort).cancelConfirmedReservations(listOf("rsv-1"))
        }
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(saga.isTimedOut()).isTrue()
    }

    @Test
    fun 재고_복원이_실패하면_주문을_취소하지_않고_다음_주기에_다시_시도하게_남긴다() {
        whenever(inventoryCommandPort.cancelReservations(listOf("rsv-1")))
            .thenThrow(ResourceAccessException("Connection refused"))

        scheduler.checkTimedOutSagas()

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        assertThat(saga.isTimedOut()).isFalse()
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        verify(sagaCompensationMetrics).recordFailed()
    }
}
