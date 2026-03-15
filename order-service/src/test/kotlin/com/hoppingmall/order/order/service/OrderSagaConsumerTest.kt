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
import com.hoppingmall.order.port.TransactionalEventPublisherPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    private lateinit var transactionalEventPublisherPort: TransactionalEventPublisherPort

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var consumer: OrderSagaConsumer

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
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.existsByEventId("payment-completed-txn-123")).thenReturn(false)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1"))).thenReturn(true)

        consumer.handlePaymentCompleted("{}")

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(orderRepository).save(order)
        verify(sagaEventLogRepository).save(any<SagaEventLog>())
    }

    @Test
    fun 이미_처리된_결제_완료_이벤트는_무시한다() {
        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.existsByEventId("payment-completed-txn-123")).thenReturn(true)

        consumer.handlePaymentCompleted("{}")

        verify(orderRepository, never()).findById(any())
    }

    @Test
    fun 예약_확정_실패_시_주문_취소하고_결제_역보상을_요청한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.existsByEventId("payment-completed-txn-123")).thenReturn(false)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1"))).thenReturn(false)

        consumer.handlePaymentCompleted("{}")

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelReservation("rsv-1")
        verify(transactionalEventPublisherPort).publishEvent(
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
        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 2L, productId = 3L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1
        )

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.existsByEventId("payment-completed-txn-123")).thenReturn(false)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))

        consumer.handlePaymentCompleted("{}")

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(inventoryCommandPort, never()).confirmReservations(any())
    }

    @Test
    fun 주문이_CREATED가_아닌_경우_상태_변경_없이_멱등_로그만_저장한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAID)

        whenever(objectMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java)))
            .thenReturn(paymentCompletedEvent)
        whenever(sagaEventLogRepository.existsByEventId("payment-completed-txn-123")).thenReturn(false)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

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

        val node = mock<com.fasterxml.jackson.databind.JsonNode>()
        whenever(objectMapper.readTree(any<String>())).thenReturn(node)
        whenever(node.get("eventId")).thenReturn(TextNode("evt-comp-1"))
        whenever(node.get("orderId")).thenReturn(LongNode(1L))
        whenever(sagaEventLogRepository.existsByEventId("evt-comp-1")).thenReturn(false)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))

        consumer.handlePaymentCompensation("{}")

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelReservation("rsv-1")
        verify(sagaEventLogRepository).save(any<SagaEventLog>())
    }

    @Test
    fun 이미_처리된_보상_이벤트는_무시한다() {
        val node = mock<com.fasterxml.jackson.databind.JsonNode>()
        whenever(objectMapper.readTree(any<String>())).thenReturn(node)
        whenever(node.get("eventId")).thenReturn(TextNode("evt-comp-1"))
        whenever(sagaEventLogRepository.existsByEventId("evt-comp-1")).thenReturn(true)

        consumer.handlePaymentCompensation("{}")

        verify(orderRepository, never()).findById(any())
    }
}
