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
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 완료 주문에 온 결제 취소 보상")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaPaidOrderPaymentCancelledTest {

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

    private var savedLog: SagaEventLog? = null

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        Mockito.lenient().`when`(sagaEventLogRepository.findByEventId(EVENT_ID)).thenAnswer { savedLog }
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { invocation ->
            savedLog = invocation.arguments[0] as SagaEventLog
            savedLog
        }
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = ORDER_ID, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("5000.00"),
                    quantity = 1, reservationId = RESERVATION_ID
                )
            )
        )
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
    }

    @Test
    fun 결제_완료_주문에_결제_취소_보상이_오면_주문을_취소하고_확정_재고까지_돌리고_취소_알림을_낸다() {
        val order = orderOf(OrderStatus.PAYING, OrderStatus.PAID)

        consumer.handlePaymentCompensation(PAYMENT_CANCELLED_MESSAGE)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelReservations(listOf(RESERVATION_ID))
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        assertThat(publishedNotification()["userId"]).isEqualTo(BUYER_ID)
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
    }

    @Test
    fun 취소_실패_주문에_결제_취소_보상이_오면_주문을_취소하고_확정_재고까지_돌린다() {
        val order = orderOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCEL_FAILED)

        consumer.handlePaymentCompensation(PAYMENT_CANCELLED_MESSAGE)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
    }

    @Test
    fun 재고_복원이_실패해_다시_전달되면_주문_취소는_한_번이고_확정_재고_복원을_다시_한다() {
        val order = orderOf(OrderStatus.PAYING, OrderStatus.PAID)
        Mockito.doThrow(RuntimeException("product down")).doNothing()
            .`when`(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))

        assertThatThrownBy { consumer.handlePaymentCompensation(PAYMENT_CANCELLED_MESSAGE) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("product down")
        consumer.handlePaymentCompensation(PAYMENT_CANCELLED_MESSAGE)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort, times(2)).cancelConfirmedReservations(listOf(RESERVATION_ID))
        verify(inventoryCommandPort, never()).cancelReservation(any())
        verify(transactionalEventPublisher, times(1)).publishEvent(
            any(), any(), eq("OrderCancellationNotificationRequested"), any(), any(), any()
        )
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
    }

    private fun orderOf(vararg path: OrderStatus): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("5000.00"))
        path.forEach { order.updateStatus(it) }
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        return order
    }

    private fun publishedNotification(): Map<String, Any?> {
        val captor = argumentCaptor<Any>()
        verify(transactionalEventPublisher).publishEvent(
            eq("Order"), eq(ORDER_ID.toString()), eq("OrderCancellationNotificationRequested"), captor.capture(), eq("notification"), eq(ORDER_ID.toString())
        )
        @Suppress("UNCHECKED_CAST")
        return captor.firstValue as Map<String, Any?>
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val BUYER_ID = 10L
        private const val EVENT_ID = "payment-cancelled-evt-853"
        private const val RESERVATION_ID = "rsv-1"
        private const val PAYMENT_CANCELLED_MESSAGE =
            """{"eventType":"PaymentCancelled","eventId":"$EVENT_ID","orderId":$ORDER_ID,"paymentId":100,"userId":$BUYER_ID,"amount":"3000.00"}"""
    }
}
