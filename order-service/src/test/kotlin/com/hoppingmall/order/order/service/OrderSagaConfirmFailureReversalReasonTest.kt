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
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("재고 확정 실패 보상의 역보상 사유")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaConfirmFailureReversalReasonTest {

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
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        val sagaLog = SagaEventLog(eventId = EVENT_ID, eventType = "PAYMENT_COMPLETED", orderId = 1L, paymentId = 100L)
            .apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }
        whenever(kafkaEventMapper.readValue(any<String>(), eq(PaymentCompletedEvent::class.java))).thenReturn(
            PaymentCompletedEvent(paymentId = 100L, orderId = 1L, userId = 10L, amount = BigDecimal("50000"), transactionId = "txn-746")
        )
        whenever(sagaEventLogRepository.findByEventId(EVENT_ID)).thenReturn(sagaLog)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(
            listOf(
                OrderItem.create(
                    orderId = 1L, sellerId = 2L, productId = 3L,
                    productName = "상품A", productPrice = BigDecimal("50000"),
                    quantity = 1, reservationId = "rsv-1"
                )
            )
        )
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 재고_확정이_실패한_역보상은_예약_만료가_아니라_재고_확정_실패를_사유로_보낸다() {
        whenever(inventoryCommandPort.confirmReservations(listOf("rsv-1"))).thenReturn(false)

        consumer.handlePaymentCompleted("{}")

        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Order"),
            aggregateId = eq("1"),
            eventType = eq("PaymentReversalRequested"),
            eventData = check<Any> {
                @Suppress("UNCHECKED_CAST")
                val data = it as Map<String, Any?>
                assertThat(data["reason"]).isEqualTo("INVENTORY_CONFIRM_FAILED")
            },
            topic = eq("payment-reversal"),
            partitionKey = eq("1")
        )
    }

    companion object {
        private const val EVENT_ID = "payment-completed-txn-746"
    }
}
