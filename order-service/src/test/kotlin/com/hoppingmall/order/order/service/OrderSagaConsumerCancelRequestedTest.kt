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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("취소 요청 중인 주문에 온 결제 취소 보상 이벤트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaConsumerCancelRequestedTest {

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
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    private val objectMapper = ObjectMapper()
    private val logs = mutableMapOf<String, SagaEventLog>()
    private lateinit var sagaConsumer: OrderSagaConsumer
    private lateinit var resultConsumer: OrderCancellationResultConsumer

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
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
                    orderId = ORDER_ID, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("50000"),
                    quantity = 1, reservationId = RESERVATION_ID
                )
            )
        )
        sagaConsumer = OrderSagaConsumer(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventoryCommandPort,
            transactionalEventPublisher,
            objectMapper,
            KafkaEventMapper(objectMapper),
            transactionTemplate,
            sagaCompensationMetrics
        )
        resultConsumer = OrderCancellationResultConsumer(
            orderRepository,
            orderItemRepository,
            sagaEventLogRepository,
            inventoryCommandPort,
            transactionalEventPublisher,
            objectMapper,
            transactionTemplate
        )
    }

    @Test
    fun 취소_요청_중인_주문에_결제_취소_보상_이벤트가_오면_주문을_CANCEL_REQUESTED로_두고_예약을_취소하지_않는다() {
        val order = cancelRequestedOrder()

        sagaConsumer.handlePaymentCompensation(PAYMENT_CANCELLED_MESSAGE)

        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_REQUESTED)
        verifyNoInteractions(inventoryCommandPort)
        verifyNoInteractions(transactionalEventPublisher)
        assertThat(logs[PAYMENT_CANCELLED_EVENT_ID]!!.isFullyCompleted()).isTrue()
    }

    @Test
    fun 결제_취소_보상_이벤트가_결과_이벤트보다_먼저_와도_결과_컨슈머가_취소를_마무리하고_알림과_확정_재고_복원을_한다() {
        val order = cancelRequestedOrder()

        sagaConsumer.handlePaymentCompensation(PAYMENT_CANCELLED_MESSAGE)
        resultConsumer.handleCancellationResult(CANCELLATION_COMPLETED_MESSAGE)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelConfirmedReservations(listOf(RESERVATION_ID))
        verify(inventoryCommandPort, never()).cancelReservation(any())
        verify(transactionalEventPublisher).publishEvent(
            eq("Order"),
            eq(ORDER_ID.toString()),
            eq("OrderCancellationNotificationRequested"),
            any(),
            eq(KafkaTopics.NOTIFICATION),
            anyOrNull()
        )
    }

    private fun cancelRequestedOrder(): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        order.requestCancellation()
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        return order
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val RESERVATION_ID = "rsv-1"
        private const val PAYMENT_CANCELLED_EVENT_ID = "payment-cancelled-1"
        private const val PAYMENT_CANCELLED_MESSAGE =
            """{"eventType":"PaymentCancelled","eventId":"$PAYMENT_CANCELLED_EVENT_ID","orderId":$ORDER_ID}"""
        private const val CANCELLATION_COMPLETED_MESSAGE =
            """{"eventType":"PaymentCancellationCompleted","eventId":"cancel-completed-1","orderId":$ORDER_ID}"""
    }
}
