package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito
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

@DisplayName("OrderSagaConsumer")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaConsumerTest {

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
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var consumer: OrderSagaConsumer

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
            transactionTemplate
        )
    }

    private val paymentCompletedEvent = PaymentCompletedEvent(
        paymentId = 100L,
        orderId = 1L,
        userId = 10L,
        amount = BigDecimal("50000"),
        pointAmount = BigDecimal("500"),
        transactionId = "txn-123"
    )

    @Test
    fun 결제_완료_시_예약_확정하고_주문_상태를_PAID로_변경한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )
        val sagaLog = SagaEventLog(
            eventId = "payment-completed-txn-123",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        ).apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.findByEventId("payment-completed-txn-123"))
            .thenReturn(null)
            .thenReturn(sagaLog)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1"))).thenReturn(true)
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentCompleted("{}")

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(orderRepository).save(order)
    }

    @Test
    fun 이미_처리된_결제_완료_이벤트는_무시한다() {
        val fullyCompleted = SagaEventLog(
            eventId = "payment-completed-txn-123",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        ).apply {
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
        }

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.findByEventId("payment-completed-txn-123"))
            .thenReturn(fullyCompleted)

        consumer.handlePaymentCompleted("{}")

        verify(orderRepository, never()).findById(any())
        verify(inventoryCommandPort, never()).confirmReservations(any())
    }

    @Test
    fun 예약_확정_실패_시_주문_취소하고_결제_역보상을_요청한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )
        val sagaLog = SagaEventLog(
            eventId = "payment-completed-txn-123",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        ).apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.findByEventId("payment-completed-txn-123"))
            .thenReturn(null)
            .thenReturn(sagaLog)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1"))).thenReturn(false)
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentCompleted("{}")

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Order"),
            aggregateId = eq("1"),
            eventType = eq("PaymentReversalRequested"),
            eventData = any(),
            topic = eq("payment-reversal"),
            partitionKey = eq("1")
        )
    }

    @Test
    fun 예약ID가_없는_주문은_바로_PAID로_전환한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1
        )
        val sagaLog = SagaEventLog(
            eventId = "payment-completed-txn-123",
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L
        ).apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.findByEventId("payment-completed-txn-123"))
            .thenReturn(null)
            .thenReturn(sagaLog)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentCompleted("{}")

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(inventoryCommandPort, never()).confirmReservations(any())
    }

    @Test
    fun 주문이_CREATED_또는_PAYING이_아닌_경우_상태_변경_없이_멱등_로그만_저장한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.findByEventId("payment-completed-txn-123")).thenReturn(null)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentCompleted("{}")

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(inventoryCommandPort, never()).confirmReservations(any())
        verify(sagaEventLogRepository).save(any<SagaEventLog>())
    }

    @Test
    fun 보상_이벤트_수신_시_주문_취소하고_예약을_해제한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )
        val sagaLog = SagaEventLog(
            eventId = "evt-comp-1",
            eventType = "PAYMENT_COMPENSATION",
            orderId = 1L
        ).apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }

        val node = mock<com.fasterxml.jackson.databind.JsonNode>()
        whenever(objectMapper.readTree(any<String>())).thenReturn(node)
        whenever(node.get("eventType")).thenReturn(TextNode("PaymentFailed"))
        whenever(node.get("eventId")).thenReturn(TextNode("evt-comp-1"))
        whenever(node.get("orderId")).thenReturn(LongNode(1L))
        whenever(sagaEventLogRepository.findByEventId("evt-comp-1"))
            .thenReturn(null)
            .thenReturn(sagaLog)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentCompensation("{}")

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelReservation("rsv-1")
    }

    @Test
    fun 이미_처리된_보상_이벤트는_무시한다() {
        val fullyCompleted = SagaEventLog(
            eventId = "evt-comp-1",
            eventType = "PAYMENT_COMPENSATION",
            orderId = 1L
        ).apply {
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
        }

        val node = mock<com.fasterxml.jackson.databind.JsonNode>()
        whenever(objectMapper.readTree(any<String>())).thenReturn(node)
        whenever(node.get("eventType")).thenReturn(TextNode("PaymentFailed"))
        whenever(node.get("eventId")).thenReturn(TextNode("evt-comp-1"))
        whenever(sagaEventLogRepository.findByEventId("evt-comp-1")).thenReturn(fullyCompleted)

        consumer.handlePaymentCompensation("{}")

        verify(orderRepository, never()).findById(any())
    }
}
