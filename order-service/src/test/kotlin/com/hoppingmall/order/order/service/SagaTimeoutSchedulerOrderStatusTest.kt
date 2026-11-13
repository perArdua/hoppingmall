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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("saga 타임아웃 보상의 주문 상태별 선점")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class SagaTimeoutSchedulerOrderStatusTest {

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
        saga = SagaEventLog(eventId = "payment-completed-txn-745", eventType = "PAYMENT_COMPLETED", orderId = ORDER_ID, paymentId = 100L)
            .apply {
                id = SAGA_ID
                markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            }
        whenever(sagaEventLogRepository.findTimedOutSagas(any())).thenReturn(listOf(saga))
        whenever(sagaEventLogRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga))
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 결제_완료_상태가_아닌_주문의_saga는_재고와_결제를_건드리지_않고_타임아웃으로만_표시한다() {
        val order = givenOrder(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED)

        scheduler.checkTimedOutSagas()

        assertThat(saga.isTimedOut()).isTrue()
        assertThat(saga.isTimeoutClaimed()).isFalse()
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun 선점한_뒤_재고_복원에_실패한_saga는_다음_주기에_복원부터_다시_해_끝낸다() {
        val order = givenOrder(OrderStatus.PAYING, OrderStatus.PAID)
        givenReservation()
        saga.claimTimeout()

        scheduler.checkTimedOutSagas()

        verify(inventoryCommandPort).cancelReservations(listOf(RESERVATION_ID))
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(saga.isTimedOut()).isTrue()
        verify(transactionalEventPublisher).publishEvent(any(), eq(ORDER_ID.toString()), eq("PaymentReversalRequested"), any(), any(), any())
    }

    @Test
    fun 선점한_사이에_사용자가_취소를_요청했으면_재고만_복원하고_역보상은_내지_않는다() {
        val order = givenOrder(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED)
        givenReservation()
        saga.claimTimeout()

        scheduler.checkTimedOutSagas()

        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_REQUESTED)
        assertThat(saga.isTimedOut()).isTrue()
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        verify(sagaCompensationMetrics, never()).recordTimeoutTriggered()
    }

    private fun givenOrder(vararg path: OrderStatus): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        path.forEach { order.updateStatus(it) }
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        return order
    }

    private fun givenReservation() {
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = ORDER_ID, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("50000"),
                    quantity = 1, reservationId = RESERVATION_ID
                )
            )
        )
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val SAGA_ID = 100L
        private const val RESERVATION_ID = "rsv-1"
    }
}
