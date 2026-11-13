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
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.util.Optional

@DisplayName("재고 확정 실패 보상의 예약·확정 재고 복원")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaConfirmFailureCompensationTest {

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
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var kafkaEventMapper: KafkaEventMapper

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    private lateinit var consumer: OrderSagaConsumer

    private lateinit var order: Order

    private lateinit var sagaLog: SagaEventLog

    private val event = PaymentCompletedEvent(
        paymentId = 100L,
        orderId = 1L,
        userId = 10L,
        amount = BigDecimal("50000"),
        pointAmount = BigDecimal("500"),
        transactionId = "txn-744"
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
            objectMapper,
            kafkaEventMapper,
            transactionTemplate,
            sagaCompensationMetrics
        )
        order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        sagaLog = SagaEventLog(eventId = EVENT_ID, eventType = "PAYMENT_COMPLETED", orderId = 1L, paymentId = 100L)
            .apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )

        whenever(kafkaEventMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java))).thenReturn(event)
        whenever(sagaEventLogRepository.findByEventId(EVENT_ID)).thenReturn(null).thenReturn(sagaLog)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 확정_응답이_시간_초과로_유실되면_확정된_재고까지_복원을_요청하고_주문을_취소한다() {
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1")))
            .thenThrow(ResourceAccessException("Read timed out", SocketTimeoutException("Read timed out")))

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf("rsv-1"))
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verifyReversalPublished()
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isTrue()
    }

    @Test
    fun 확정_호출이_5xx로_끝나도_확정된_재고까지_복원을_요청한다() {
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1")))
            .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "error", HttpHeaders.EMPTY, ByteArray(0), null))

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf("rsv-1"))
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun 확정이_명시적으로_실패하면_예약을_취소해_재고를_바로_돌려준다() {
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1"))).thenReturn(false)

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verifyReversalPublished()
    }

    @Test
    fun 확정이_409로_거절되면_예약을_취소해_재고를_바로_돌려준다() {
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1")))
            .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "INV004", HttpHeaders.EMPTY, ByteArray(0), null))

        consumer.handlePaymentCompleted("{}")

        verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun 예약_취소를_확정_재고_복원보다_먼저_요청한다() {
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1")))
            .thenThrow(ResourceAccessException("Read timed out", SocketTimeoutException("Read timed out")))

        consumer.handlePaymentCompleted("{}")

        inOrder(inventoryCommandPort) {
            verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
            verify(inventoryCommandPort).cancelConfirmedReservations(listOf("rsv-1"))
        }
    }

    @Test
    fun 재고_복원_요청이_실패하면_주문을_취소하지_않고_예외를_올려_다시_처리되게_한다() {
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1")))
            .thenThrow(ResourceAccessException("Read timed out", SocketTimeoutException("Read timed out")))
        whenever(inventoryCommandPort.cancelReservations(listOf("rsv-1")))
            .thenThrow(ResourceAccessException("Connection refused"))

        assertThatThrownBy { consumer.handlePaymentCompleted("{}") }
            .isInstanceOf(ResourceAccessException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
        assertThat(sagaLog.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isFalse()
    }

    private fun verifyReversalPublished() {
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Order"),
            aggregateId = eq("1"),
            eventType = eq("PaymentReversalRequested"),
            eventData = any(),
            topic = eq("payment-reversal"),
            partitionKey = eq("1")
        )
    }

    companion object {
        private const val EVENT_ID = "payment-completed-txn-744"
    }
}
