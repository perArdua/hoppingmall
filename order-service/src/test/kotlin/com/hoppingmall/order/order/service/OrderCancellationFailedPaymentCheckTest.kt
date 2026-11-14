package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 취소 실패 이벤트 처리의 결제 상태 확인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCancellationFailedPaymentCheckTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var paymentQueryPort: PaymentQueryPort

    private val objectMapper = ObjectMapper()

    private val logs = mutableMapOf<String, SagaEventLog>()

    private lateinit var consumer: OrderCancellationResultConsumer

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            invocation.getArgument<TransactionCallback<Any>>(0).doInTransaction(mock())
        }
        Mockito.lenient().`when`(sagaEventLogRepository.findByEventId(any())).thenAnswer { logs[it.arguments[0] as String] }
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer {
            val log = it.arguments[0] as SagaEventLog
            logs[log.eventId] = log
            log
        }
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = ORDER_ID, sellerId = 1L, productId = 10L, productName = "상품",
                    productPrice = BigDecimal("50000"), quantity = 1, reservationId = RESERVATION_ID
                )
            )
        )
        consumer = OrderCancellationResultConsumer(
            orderRepository, orderItemRepository, sagaEventLogRepository,
            inventoryCommandPort, transactionalEventPublisher,
            objectMapper, transactionTemplate, paymentQueryPort
        )
    }

    @Test
    fun 결제가_이미_취소됐으면_늦게_온_취소_실패_이벤트는_주문을_CANCELLED로_마무리하고_재고를_돌려준다() {
        val order = cancelRequestedOrder()
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(payment("CANCELLED"))

        consumer.handleCancellationResult(failedMessage(FAILED_EVENT_ID))

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        verify(transactionalEventPublisher).publishEvent(any(), any(), eq("OrderCancellationNotificationRequested"), any(), any(), any())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), eq("OrderCancellationFailedNotificationRequested"), any(), any(), any())
        assertThat(logs.getValue(FAILED_EVENT_ID).isFullyCompleted()).isTrue()
    }

    @Test
    fun 결제가_이미_취소돼_마무리한_뒤_같은_회차의_취소_완료_이벤트가_와도_재고를_한_번만_돌려준다() {
        val order = cancelRequestedOrder()
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(payment("CANCELLED"))

        consumer.handleCancellationResult(failedMessage(FAILED_EVENT_ID))
        consumer.handleCancellationResult(completedMessage(COMPLETED_EVENT_ID))

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
    }

    @Test
    fun 결제_조회가_안_되면_주문을_바꾸지_않고_예외를_던져_다시_처리되게_한다() {
        val order = cancelRequestedOrder()
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID))
            .thenThrow(PaymentQueryUnavailableException("주문별 결제 조회 실패 orderId=$ORDER_ID", RuntimeException("연결 실패")))

        assertThatThrownBy { consumer.handleCancellationResult(failedMessage(FAILED_EVENT_ID)) }
            .isInstanceOf(PaymentQueryUnavailableException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_REQUESTED)
        assertThat(logs).doesNotContainKey(FAILED_EVENT_ID)
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun 결제가_취소되지_않았으면_CANCEL_FAILED로_바꾼다() {
        val order = cancelRequestedOrder()
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(payment("SUCCESS"))

        consumer.handleCancellationResult(failedMessage(FAILED_EVENT_ID))

        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_FAILED)
        verify(inventoryCommandPort, never()).cancelConfirmedReservations(any())
        verify(transactionalEventPublisher).publishEvent(any(), any(), eq("OrderCancellationFailedNotificationRequested"), any(), any(), any())
    }

    @Test
    fun 재고_복구가_실패해_다시_온_취소_실패_이벤트는_결제를_다시_조회하지_않고_재고_복구를_이어서_한다() {
        val order = cancelRequestedOrder()
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(payment("CANCELLED"))
        Mockito.doThrow(RuntimeException("product 연결 실패")).doNothing()
            .`when`(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))

        assertThatThrownBy { consumer.handleCancellationResult(failedMessage(FAILED_EVENT_ID)) }
            .isInstanceOf(RuntimeException::class.java)
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)

        consumer.handleCancellationResult(failedMessage(FAILED_EVENT_ID))

        verify(paymentQueryPort).findByOrderIdOrThrowIfUnavailable(ORDER_ID)
        verify(inventoryCommandPort, Mockito.times(2)).cancelConfirmedReservations(listOf(RESERVATION_ID))
        assertThat(logs.getValue(FAILED_EVENT_ID).isFullyCompleted()).isTrue()
    }

    @Test
    fun CANCEL_REQUESTED가_아닌_주문의_취소_실패_이벤트는_결제를_조회하지_않는다() {
        val order = cancelRequestedOrder()
        order.updateStatus(OrderStatus.CANCELLED)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))

        consumer.handleCancellationResult(failedMessage(FAILED_EVENT_ID))

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(paymentQueryPort, never()).findByOrderIdOrThrowIfUnavailable(any())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
    }

    private fun cancelRequestedOrder(): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
    }

    private fun payment(status: String) = PaymentInfo(
        id = 1L, orderId = ORDER_ID, amount = BigDecimal("50000"),
        pointAmount = BigDecimal.ZERO, couponId = null, status = status
    )

    private fun failedMessage(eventId: String) = objectMapper.writeValueAsString(
        mapOf(
            "eventType" to "PaymentCancellationFailed",
            "eventId" to eventId,
            "orderId" to ORDER_ID,
            "reason" to "결제 취소 처리 중 오류"
        )
    )

    private fun completedMessage(eventId: String) = objectMapper.writeValueAsString(
        mapOf(
            "eventType" to "PaymentCancellationCompleted",
            "eventId" to eventId,
            "orderId" to ORDER_ID,
            "paymentId" to 1L
        )
    )

    companion object {
        private const val ORDER_ID = 100L
        private const val RESERVATION_ID = "rsv-1"
        private const val FAILED_EVENT_ID = "cancel-failed-evt-round-1"
        private const val COMPLETED_EVENT_ID = "cancel-completed-evt-round-2"
    }
}
