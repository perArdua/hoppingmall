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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.ResourceAccessException
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 보상의 예약 취소 실패 재시도")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaCompensationReservationCancelRetryTest {

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

    private lateinit var order: Order

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
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = ORDER_ID, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("50000"),
                    quantity = 1, reservationId = RESERVATION_ID
                )
            )
        )
        order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
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
    fun 예약_취소가_실패하면_예외를_올리고_보상을_끝난_것으로_표시하지_않는다() {
        doThrow(ResourceAccessException("Connection refused")).whenever(inventoryCommandPort).cancelReservation(RESERVATION_ID)

        assertThatThrownBy { consumer.handlePaymentCompensation(COMPENSATION_MESSAGE) }
            .isInstanceOf(ResourceAccessException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(savedLog!!.isStepCompleted(SagaEventLog.LOCAL_COMPLETED)).isTrue()
        assertThat(savedLog!!.isStepCompleted(SagaEventLog.REMOTE_COMPLETED)).isFalse()
        verify(sagaCompensationMetrics, never()).recordSuccess()
    }

    @Test
    fun 예약_취소가_실패한_보상은_다시_전달되면_예약_취소를_다시_요청해_끝낸다() {
        doThrow(ResourceAccessException("Connection refused"))
            .doNothing()
            .whenever(inventoryCommandPort).cancelReservation(RESERVATION_ID)

        runCatching { consumer.handlePaymentCompensation(COMPENSATION_MESSAGE) }
        consumer.handlePaymentCompensation(COMPENSATION_MESSAGE)

        verify(inventoryCommandPort, times(2)).cancelReservation(RESERVATION_ID)
        assertThat(savedLog!!.isFullyCompleted()).isTrue()
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val EVENT_ID = "evt-comp-746"
        private const val RESERVATION_ID = "rsv-1"
        private const val COMPENSATION_MESSAGE =
            """{"eventType":"PaymentFailed","eventId":"$EVENT_ID","orderId":$ORDER_ID}"""
    }
}
