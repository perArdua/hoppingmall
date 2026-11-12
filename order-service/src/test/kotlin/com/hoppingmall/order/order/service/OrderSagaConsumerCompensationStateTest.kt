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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 보상 처리의 주문 상태별 예약 취소")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaConsumerCompensationStateTest {

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
        whenever(sagaEventLogRepository.findByEventId(EVENT_ID)).thenAnswer { savedLog }
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { invocation ->
            savedLog = invocation.arguments[0] as SagaEventLog
            savedLog
        }
        Mockito.lenient().`when`(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = ORDER_ID, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("50000"),
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
    fun 배송_중인_주문에_결제_보상_이벤트가_오면_예약을_취소하지_않고_처리_완료로_남긴다() {
        val order = orderOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED)

        consumer.handlePaymentCompensation(COMPENSATION_MESSAGE)

        verify(inventoryCommandPort, never()).cancelReservation(any())
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
    }

    @Test
    fun 배송_완료된_주문에_결제_보상_이벤트가_오면_예약을_취소하지_않고_처리_완료로_남긴다() {
        val order = orderOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)

        consumer.handlePaymentCompensation(COMPENSATION_MESSAGE)

        verify(inventoryCommandPort, never()).cancelReservation(any())
        assertThat(order.status).isEqualTo(OrderStatus.DELIVERED)
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
    }

    @Test
    fun 이미_취소된_주문에_결제_보상_이벤트가_오면_예약을_다시_취소하지_않고_처리_완료로_남긴다() {
        val order = orderOf(OrderStatus.PAYING, OrderStatus.CANCELLED)

        consumer.handlePaymentCompensation(COMPENSATION_MESSAGE)

        verify(inventoryCommandPort, never()).cancelReservation(any())
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
    }

    @Test
    fun 결제_대기_주문에_결제_보상_이벤트가_오면_주문을_취소하고_예약을_취소한다() {
        val order = orderOf(OrderStatus.PAYING)

        consumer.handlePaymentCompensation(COMPENSATION_MESSAGE)

        verify(inventoryCommandPort).cancelReservation(RESERVATION_ID)
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
    }

    private fun orderOf(vararg path: OrderStatus): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        path.forEach { order.updateStatus(it) }
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        return order
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val EVENT_ID = "payment-cancelled-evt-1"
        private const val RESERVATION_ID = "rsv-1"
        private const val COMPENSATION_MESSAGE =
            """{"eventType":"PaymentCancelled","eventId":"$EVENT_ID","orderId":$ORDER_ID}"""
    }
}
