package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
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
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("주문 취소 알림의 주문 id metadata")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCancellationNotificationMetadataTest {

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
    }

    @Test
    fun 결제_취소_완료로_취소된_주문의_알림에_주문_id가_있다() {
        stubOrder(cancelRequestedOrder())

        cancellationResultConsumer().handleCancellationResult(
            message("eventType" to "PaymentCancellationCompleted", "eventId" to "completed-1", "orderId" to ORDER_ID, "paymentId" to 1L)
        )

        assertOrderIdMetadata("OrderCancellationNotificationRequested")
    }

    @Test
    fun 결제_취소_실패_알림에_주문_id가_있다() {
        stubOrder(cancelRequestedOrder())
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(payment("SUCCESS"))

        cancellationResultConsumer().handleCancellationResult(
            message("eventType" to "PaymentCancellationFailed", "eventId" to "failed-1", "orderId" to ORDER_ID, "reason" to "오류")
        )

        assertOrderIdMetadata("OrderCancellationFailedNotificationRequested")
    }

    @Test
    fun 결제_완료_주문의_결제_취소_보상_알림에_주문_id가_있다() {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        stubOrder(order)
        val consumer = OrderSagaConsumer(
            sagaEventLogRepository, orderRepository, orderItemRepository, inventoryCommandPort,
            transactionalEventPublisher, objectMapper, mock<KafkaEventMapper>(), transactionTemplate, mock<SagaCompensationMetrics>()
        )

        consumer.handlePaymentCompensation(
            message(
                "eventType" to "PaymentCancelled", "eventId" to "payment-cancelled-1", "orderId" to ORDER_ID,
                "paymentId" to 100, "userId" to BUYER_ID, "amount" to "50000.00"
            )
        )

        assertOrderIdMetadata("OrderCancellationNotificationRequested")
    }

    @Test
    fun 취소_요청_시간_초과로_취소된_주문의_알림에_주문_id가_있다() {
        val order = cancelRequestedOrder()
        stubOrder(order)
        whenever(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PAYING), any(), any())).thenReturn(emptyList())
        whenever(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.CANCEL_REQUESTED), any(), any())).thenReturn(listOf(order))
        whenever(paymentQueryPort.findByOrderIdOrThrowIfUnavailable(ORDER_ID)).thenReturn(payment("CANCELLED"))
        val scheduler = OrderTimeoutScheduler(
            orderRepository, orderItemRepository, mock<OrderCommandService>(), paymentQueryPort,
            inventoryCommandPort, transactionalEventPublisher, transactionTemplate
        )

        scheduler.checkTimedOutOrders()

        assertOrderIdMetadata("OrderCancellationNotificationRequested")
    }

    private fun assertOrderIdMetadata(eventType: String) {
        val captor = argumentCaptor<Any>()
        verify(transactionalEventPublisher).publishEvent(any(), any(), eq(eventType), captor.capture(), eq(KafkaTopics.NOTIFICATION), any())
        @Suppress("UNCHECKED_CAST")
        val eventData = captor.firstValue as Map<String, Any?>
        val metadata = eventData["metadata"]
        assertThat(metadata).isInstanceOf(String::class.java)
        val node = objectMapper.readTree(metadata as String)
        assertThat(node.path("orderId").isIntegralNumber).isTrue()
        assertThat(node.path("orderId").asLong()).isEqualTo(ORDER_ID)
    }

    private fun cancellationResultConsumer() = OrderCancellationResultConsumer(
        orderRepository, orderItemRepository, sagaEventLogRepository,
        inventoryCommandPort, transactionalEventPublisher,
        objectMapper, transactionTemplate, paymentQueryPort
    )

    private fun stubOrder(order: Order) {
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
    }

    private fun cancelRequestedOrder(): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        return order
    }

    private fun payment(status: String) = PaymentInfo(
        id = 1L, orderId = ORDER_ID, amount = BigDecimal("50000"),
        pointAmount = BigDecimal.ZERO, couponId = null, status = status
    )

    private fun message(vararg fields: Pair<String, Any>): String = objectMapper.writeValueAsString(mapOf(*fields))

    companion object {
        private const val ORDER_ID = 1L
        private const val BUYER_ID = 10L
        private const val RESERVATION_ID = "rsv-1"
    }
}
