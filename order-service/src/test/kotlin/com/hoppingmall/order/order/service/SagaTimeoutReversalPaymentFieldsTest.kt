package com.hoppingmall.order.order.service

import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.port.PaymentQueryUnavailableException
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("saga 시간 초과 역보상의 결제 ID·결제액")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class SagaTimeoutReversalPaymentFieldsTest {

    @Mock
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var paymentQueryPort: PaymentQueryPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    private lateinit var scheduler: SagaTimeoutScheduler

    private lateinit var order: Order

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }
        order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("5000.00"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        Mockito.lenient().`when`(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        scheduler = SagaTimeoutScheduler(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventoryCommandPort,
            paymentQueryPort,
            transactionalEventPublisher,
            transactionTemplate,
            sagaCompensationMetrics
        )
    }

    @Test
    fun saga에_결제_ID와_결제액이_있으면_조회하지_않고_역보상에_그대로_담는다() {
        timedOutSaga(paymentId = PAYMENT_ID, paymentAmount = BigDecimal("3000.00"))

        scheduler.checkTimedOutSagas()

        val data = publishedReversal()
        assertThat(data["paymentId"]).isEqualTo(PAYMENT_ID)
        assertThat(data["amount"]).isEqualTo("3000.00")
        verify(paymentQueryPort, never()).findByOrderIdOrThrowIfUnavailable(any())
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun saga에_결제_정보가_없으면_주문_ID로_결제를_조회해_결제_ID와_결제액을_채운다() {
        timedOutSaga(paymentId = null, paymentAmount = null)
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(
            PaymentInfo(id = 400L, orderId = ORDER_ID, amount = BigDecimal("2500.00"), pointAmount = BigDecimal.ZERO, couponId = 7L, status = "SUCCESS")
        )

        scheduler.checkTimedOutSagas()

        val data = publishedReversal()
        assertThat(data["paymentId"]).isEqualTo(400L)
        assertThat(data["amount"]).isEqualTo("2500.00")
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun saga의_결제_ID가_0이면_없는_것으로_보고_결제를_조회한다() {
        timedOutSaga(paymentId = 0L, paymentAmount = BigDecimal("3000.00"))
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(
            PaymentInfo(id = 401L, orderId = ORDER_ID, amount = BigDecimal("3000.00"), pointAmount = BigDecimal.ZERO, couponId = null, status = "SUCCESS")
        )

        scheduler.checkTimedOutSagas()

        val data = publishedReversal()
        assertThat(data["paymentId"]).isEqualTo(401L)
        assertThat(data["amount"]).isEqualTo("3000.00")
    }

    @Test
    fun 결제_조회가_불가하면_이번_주기에는_재고를_돌리지_않고_주문을_그대로_두며_선점을_유지한다() {
        val saga = timedOutSaga(paymentId = null, paymentAmount = null)
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID))
            .thenThrow(PaymentQueryUnavailableException("payment down", RuntimeException()))

        scheduler.checkTimedOutSagas()

        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), eq("PaymentReversalRequested"), any(), any(), any())
        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(inventoryCommandPort, never()).cancelConfirmedReservations(any())
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        assertThat(saga.isTimeoutClaimed()).isTrue()
        assertThat(saga.isTimedOut()).isFalse()
        verify(sagaCompensationMetrics).recordFailed()
    }

    @Test
    fun 결제가_없다고_답하면_결제_ID_0과_결제액_0으로_역보상을_낸다() {
        timedOutSaga(paymentId = null, paymentAmount = null)
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(null)

        scheduler.checkTimedOutSagas()

        val data = publishedReversal()
        assertThat(data["paymentId"]).isEqualTo(0L)
        assertThat(data["amount"]).isEqualTo("0")
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    private fun timedOutSaga(paymentId: Long?, paymentAmount: BigDecimal?): SagaEventLog {
        val saga = SagaEventLog(
            eventId = "payment-completed-txn-852",
            eventType = "PAYMENT_COMPLETED",
            orderId = ORDER_ID,
            paymentId = paymentId,
            paymentAmount = paymentAmount,
            timeoutAt = LocalDateTime.now().minusMinutes(1)
        ).apply {
            id = SAGA_ID
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
        }
        whenever(sagaEventLogRepository.findTimedOutSagas(any())).thenReturn(listOf(saga))
        whenever(sagaEventLogRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga))
        return saga
    }

    private fun publishedReversal(): Map<String, Any?> {
        val captor = argumentCaptor<Any>()
        verify(transactionalEventPublisher).publishEvent(
            eq("Order"), eq(ORDER_ID.toString()), eq("PaymentReversalRequested"), captor.capture(), eq("payment-reversal"), eq(ORDER_ID.toString())
        )
        @Suppress("UNCHECKED_CAST")
        return captor.firstValue as Map<String, Any?>
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val SAGA_ID = 852L
        private const val PAYMENT_ID = 100L
        private const val BUYER_ID = 10L
    }
}
