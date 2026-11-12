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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("결제 취소 완료 처리의 확정된 예약 취소")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCancellationResultConfirmedReservationTest {

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

    private fun paidOrder(): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        return order
    }

    private fun cancelRequestedOrder(): Order {
        val order = paidOrder()
        order.updateStatus(OrderStatus.CANCEL_REQUESTED)
        return order
    }

    private fun stubTransactionTemplate() {
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<Any>>(0)
            callback.doInTransaction(mock())
        }
    }

    private fun stubCancelRequested(order: Order) {
        stubTransactionTemplate()
        whenever(sagaEventLogRepository.findByEventId(EVENT_ID)).thenReturn(null)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                orderItem(productId = 10L, reservationId = "rsv-1"),
                orderItem(productId = 20L, reservationId = "rsv-2")
            )
        )
    }

    private fun orderItem(productId: Long, reservationId: String) = OrderItem.create(
        orderId = ORDER_ID, sellerId = 1L, productId = productId, productName = "상품",
        productPrice = BigDecimal("25000"), quantity = 1, reservationId = reservationId
    )

    private fun cancellationCompletedMessage() = objectMapper.writeValueAsString(
        mapOf(
            "eventType" to "PaymentCancellationCompleted",
            "eventId" to EVENT_ID,
            "orderId" to ORDER_ID,
            "paymentId" to 1L
        )
    )

    @Test
    fun 결제_취소_완료를_받으면_주문을_CANCELLED로_바꾸고_확정된_예약_취소를_주문의_예약_id로_한_번_부른다() {
        val order = cancelRequestedOrder()
        stubCancelRequested(order)

        createConsumer().handleCancellationResult(cancellationCompletedMessage())

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort, times(1)).cancelConfirmedReservations(listOf("rsv-1", "rsv-2"))
    }

    @Test
    fun 결제_취소_완료를_받으면_기존_배치_취소와_단건_취소는_부르지_않는다() {
        val order = cancelRequestedOrder()
        stubCancelRequested(order)

        createConsumer().handleCancellationResult(cancellationCompletedMessage())

        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(inventoryCommandPort, never()).cancelReservation(any())
        verify(inventoryCommandPort, never()).increaseStock(any(), any())
    }

    @Test
    fun CANCEL_REQUESTED가_아닌_주문이면_어떤_예약_취소도_부르지_않는다() {
        val order = paidOrder()
        stubTransactionTemplate()
        whenever(sagaEventLogRepository.findByEventId(EVENT_ID)).thenReturn(null)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))

        createConsumer().handleCancellationResult(cancellationCompletedMessage())

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verifyNoInteractions(inventoryCommandPort)
    }

    companion object {
        private const val ORDER_ID = 100L
        private const val EVENT_ID = "cancel-completed-confirmed-1"
    }
}
