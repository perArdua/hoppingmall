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
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("OrderCancellationResultConsumer")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCancellationResultConsumerTest {

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

    private val objectMapper = ObjectMapper()

    private fun createConsumer() = OrderCancellationResultConsumer(
        orderRepository, orderItemRepository, sagaEventLogRepository,
        inventoryCommandPort, transactionalEventPublisher,
        objectMapper, transactionTemplate
    )

    private fun createOrder(status: OrderStatus): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        if (status == OrderStatus.CANCEL_REQUESTED) {
            order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        }
        ReflectionTestUtils.setField(order, "id", 100L)
        return order
    }

    private fun setupTransactionTemplate() {
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<Any>>(0)
            callback.doInTransaction(mock())
        }
    }

    @Test
    fun PaymentCancellationCompleted_수신_시_CANCELLED_전환_및_재고_복구() {
        val consumer = createConsumer()
        val order = createOrder(OrderStatus.CANCEL_REQUESTED)

        setupTransactionTemplate()
        whenever(sagaEventLogRepository.findByEventId("cancel-completed-evt-1")).thenReturn(null)
        whenever(orderRepository.findById(100L)).thenReturn(Optional.of(order))
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        val items = listOf(
            OrderItem.create(orderId = 100L, sellerId = 1L, productId = 10L, productName = "상품", productPrice = BigDecimal("50000"), quantity = 1, reservationId = "rsv-1")
        )
        whenever(orderItemRepository.findByOrderId(100L)).thenReturn(items)

        val message = objectMapper.writeValueAsString(mapOf(
            "eventType" to "PaymentCancellationCompleted",
            "eventId" to "cancel-completed-evt-1",
            "orderId" to 100L,
            "paymentId" to 1L
        ))

        consumer.handleCancellationResult(message)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
    }

    @Test
    fun PaymentCancellationFailed_수신_시_CANCEL_FAILED_전환() {
        val consumer = createConsumer()
        val order = createOrder(OrderStatus.CANCEL_REQUESTED)

        setupTransactionTemplate()
        whenever(sagaEventLogRepository.findByEventId("cancel-failed-evt-2")).thenReturn(null)
        whenever(orderRepository.findById(100L)).thenReturn(Optional.of(order))
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }

        val message = objectMapper.writeValueAsString(mapOf(
            "eventType" to "PaymentCancellationFailed",
            "eventId" to "cancel-failed-evt-2",
            "orderId" to 100L,
            "reason" to "결제 상태 부적합"
        ))

        consumer.handleCancellationResult(message)

        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_FAILED)
        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(transactionalEventPublisher).publishEvent(any(), any(), eq("OrderCancellationFailedNotificationRequested"), any(), any(), any())
    }

    @Test
    fun 이미_처리된_이벤트는_스킵한다() {
        val consumer = createConsumer()
        val completedLog = SagaEventLog(
            eventId = "cancel-completed-evt-3",
            eventType = "PAYMENT_CANCELLATION_COMPLETED",
            orderId = 100L
        ).apply {
            markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
            markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
        }

        whenever(sagaEventLogRepository.findByEventId("cancel-completed-evt-3")).thenReturn(completedLog)

        val message = objectMapper.writeValueAsString(mapOf(
            "eventType" to "PaymentCancellationCompleted",
            "eventId" to "cancel-completed-evt-3",
            "orderId" to 100L,
            "paymentId" to 1L
        ))

        consumer.handleCancellationResult(message)

        verify(orderRepository, never()).findById(any())
        verify(transactionTemplate, never()).execute(any<TransactionCallback<Any>>())
    }

    @Test
    fun CANCEL_REQUESTED_아닌_주문은_스킵한다() {
        val consumer = createConsumer()
        val order = createOrder(OrderStatus.CANCEL_REQUESTED)
        order.updateStatus(OrderStatus.CANCELLED)

        setupTransactionTemplate()
        whenever(sagaEventLogRepository.findByEventId("cancel-completed-evt-4")).thenReturn(null)
        whenever(orderRepository.findById(100L)).thenReturn(Optional.of(order))

        val message = objectMapper.writeValueAsString(mapOf(
            "eventType" to "PaymentCancellationCompleted",
            "eventId" to "cancel-completed-evt-4",
            "orderId" to 100L,
            "paymentId" to 1L
        ))

        consumer.handleCancellationResult(message)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 관련_없는_이벤트_타입은_무시한다() {
        val consumer = createConsumer()

        val message = objectMapper.writeValueAsString(mapOf(
            "eventType" to "PaymentReversalRequested",
            "eventId" to "reversal-1",
            "orderId" to 100L
        ))

        consumer.handleCancellationResult(message)

        verify(orderRepository, never()).findById(any())
    }
}
