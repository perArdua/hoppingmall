package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.dto.event.PaymentCompletedEvent
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

@DisplayName("결제 완료 처리의 주문 상태별 재고 확정 결정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaPaymentCompletedOrderStatusTest {

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
    private lateinit var kafkaEventMapper: KafkaEventMapper

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    private lateinit var consumer: OrderSagaConsumer

    private lateinit var sagaLog: SagaEventLog

    private val event = PaymentCompletedEvent(
        paymentId = 100L,
        orderId = ORDER_ID,
        userId = 10L,
        amount = BigDecimal("50000"),
        transactionId = "txn-745"
    )

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        consumer = OrderSagaConsumer(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventoryCommandPort,
            transactionalEventPublisher,
            ObjectMapper(),
            kafkaEventMapper,
            transactionTemplate,
            sagaCompensationMetrics
        )
        sagaLog = SagaEventLog(eventId = EVENT_ID, eventType = "PAYMENT_COMPLETED", orderId = ORDER_ID, paymentId = 100L)
            .apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }
        whenever(kafkaEventMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java))).thenReturn(event)
        whenever(sagaEventLogRepository.findByEventId(EVENT_ID)).thenReturn(sagaLog)
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = ORDER_ID, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("50000"),
                    quantity = 1, reservationId = RESERVATION_ID
                )
            )
        )
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 시간_초과_보상이_선점한_saga의_결제_완료는_재고와_주문을_건드리지_않는다() {
        sagaLog.claimTimeout()

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort, never()).confirmReservations(any())
        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isFalse()
    }

    @Test
    fun 취소_요청_중인_주문에_결제_완료가_다시_오면_확정하지_않고_확정_전_예약만_푼다() {
        givenOrder(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED)

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort, never()).confirmReservations(any())
        verify(inventoryCommandPort).cancelReservations(listOf(RESERVATION_ID))
        verify(inventoryCommandPort, never()).cancelConfirmedReservations(any())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isTrue()
    }

    @Test
    fun 취소된_주문에_결제_완료가_다시_오면_예약과_확정_재고를_풀고_늦은_결제_취소를_요청한다() {
        givenOrder(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED)

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort, never()).confirmReservations(any())
        verify(inventoryCommandPort).cancelReservations(listOf(RESERVATION_ID))
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        verifyPublished("PaymentCancellationRequested")
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), eq("PaymentReversalRequested"), any(), any(), any())
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isTrue()
    }

    @Test
    fun 재고_확정_중에_주문이_취소되면_확정한_재고를_되돌리고_확정으로_표시하지_않는다() {
        val order = givenOrder(OrderStatus.PAYING, OrderStatus.PAID)
        whenever(inventoryCommandPort.confirmReservations(listOf(RESERVATION_ID))).thenAnswer {
            order.updateStatus(OrderStatus.CANCEL_REQUESTED)
            order.updateStatus(OrderStatus.CANCELLED)
            true
        }

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        verifyPublished("PaymentCancellationRequested")
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun 재고_확정_중에_시간_초과_보상이_선점하면_확정_결과를_남기지_않고_시간_초과에_맡긴다() {
        givenOrder(OrderStatus.PAYING, OrderStatus.PAID)
        whenever(inventoryCommandPort.confirmReservations(listOf(RESERVATION_ID))).thenAnswer {
            sagaLog.claimTimeout()
            true
        }

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort, never()).cancelConfirmedReservations(any())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isFalse()
    }

    @Test
    fun 확정_실패_보상_때_주문이_취소_요청_중이면_역보상을_내지_않고_처리_완료로_남긴다() {
        val order = givenOrder(OrderStatus.PAYING, OrderStatus.PAID)
        whenever(inventoryCommandPort.confirmReservations(listOf(RESERVATION_ID))).thenAnswer {
            order.updateStatus(OrderStatus.CANCEL_REQUESTED)
            false
        }

        consumer.handlePaymentCompleted("{}")

        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        verify(sagaCompensationMetrics, never()).recordSuccess()
        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_REQUESTED)
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isTrue()
    }

    @Test
    fun 확정_실패_보상_때_시간_초과_보상이_선점했으면_주문을_취소하지_않고_시간_초과에_맡긴다() {
        val order = givenOrder(OrderStatus.PAYING, OrderStatus.PAID)
        whenever(inventoryCommandPort.confirmReservations(listOf(RESERVATION_ID))).thenAnswer {
            sagaLog.claimTimeout()
            false
        }

        consumer.handlePaymentCompleted("{}")

        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isFalse()
    }

    private fun givenOrder(vararg path: OrderStatus): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        path.forEach { order.updateStatus(it) }
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        return order
    }

    private fun verifyPublished(eventType: String) {
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Order"),
            aggregateId = eq(ORDER_ID.toString()),
            eventType = eq(eventType),
            eventData = any(),
            topic = any(),
            partitionKey = eq(ORDER_ID.toString())
        )
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val EVENT_ID = "payment-completed-txn-745"
        private const val RESERVATION_ID = "rsv-1"
    }
}
