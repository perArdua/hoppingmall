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
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 완료 saga가 남기는 결제액과 확정 실패 역보상의 결제 ID·결제액")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaReversalPaymentFieldsTest {

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
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("5000.00"))
        order.updateStatus(OrderStatus.PAYING)
        whenever(kafkaEventMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java))).thenReturn(
            PaymentCompletedEvent(
                paymentId = PAYMENT_ID, orderId = ORDER_ID, userId = BUYER_ID,
                amount = BigDecimal("3000.00"), transactionId = "txn-852"
            )
        )
        whenever(sagaEventLogRepository.findByEventId(EVENT_ID)).thenAnswer { savedLog }
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { invocation ->
            savedLog = invocation.arguments[0] as SagaEventLog
            savedLog
        }
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = ORDER_ID, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("5000.00"),
                    quantity = 1, reservationId = RESERVATION_ID
                )
            )
        )
    }

    @Test
    fun 결제_완료_saga는_결제_ID와_실제_결제액을_남긴다() {
        whenever(inventoryCommandPort.confirmReservations(listOf(RESERVATION_ID))).thenReturn(true)

        consumer.handlePaymentCompleted("{}")

        assertThat(savedLog!!.paymentId).isEqualTo(PAYMENT_ID)
        assertThat(savedLog!!.paymentAmount).isEqualByComparingTo("3000.00")
    }

    @Test
    fun 재고_확정이_실패한_역보상은_결제_ID와_실제_결제액을_담는다() {
        whenever(inventoryCommandPort.confirmReservations(listOf(RESERVATION_ID))).thenReturn(false)

        consumer.handlePaymentCompleted("{}")

        val captor = argumentCaptor<Any>()
        verify(transactionalEventPublisher).publishEvent(
            eq("Order"), eq(ORDER_ID.toString()), eq("PaymentReversalRequested"), captor.capture(), eq("payment-reversal"), eq(ORDER_ID.toString())
        )
        @Suppress("UNCHECKED_CAST")
        val data = captor.firstValue as Map<String, Any?>
        assertThat(data["paymentId"]).isEqualTo(PAYMENT_ID)
        assertThat(data["amount"]).isEqualTo("3000.00")
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val PAYMENT_ID = 100L
        private const val BUYER_ID = 10L
        private const val RESERVATION_ID = "rsv-1"
        private const val EVENT_ID = "payment-completed-txn-852"
    }
}
