package com.hoppingmall.mall.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.payment.domain.CompensationEventLog
import com.hoppingmall.mall.payment.domain.repository.CompensationEventLogRepository
import com.hoppingmall.mall.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.mall.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.support.fixture.cancelledFixture
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.paidFixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.*

@DisplayName("PaymentCompensationConsumer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentCompensationConsumerTest {

    private val compensationEventLogRepository: CompensationEventLogRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val inventoryCommandService: InventoryCommandService = mock()
    private val pointRepository: PointRepository = mock()
    private val pointHistoryRepository: PointHistoryRepository = mock()
    private val objectMapper = ObjectMapper()

    private val consumer = PaymentCompensationConsumer(
        compensationEventLogRepository,
        orderRepository,
        orderItemRepository,
        inventoryCommandService,
        pointRepository,
        pointHistoryRepository,
        objectMapper
    )

    @Nested
    @DisplayName("handlePaymentFailed")
    inner class HandlePaymentFailed {

        @Test
        fun `결제_실패_시_주문_취소_및_재고_복구`() {
            // given
            val event = PaymentFailedEvent(
                eventId = "payment-failed-1",
                paymentId = 1L,
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                reason = "잔액 부족"
            )

            val order = Order.paidFixture()
            val orderItem = OrderItem.fixture(orderId = 1L, productId = 100L, quantity = 2)

            whenever(compensationEventLogRepository.existsByEventId(event.eventId)).thenReturn(false)
            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(orderRepository.save(order)).thenReturn(order)
            whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
            whenever(compensationEventLogRepository.save(any<CompensationEventLog>())).thenAnswer { it.arguments[0] }

            // when
            consumer.handlePaymentFailed(event)

            // then
            assertEquals(OrderStatus.CANCELLED, order.status)
            verify(orderRepository).save(order)
            verify(inventoryCommandService).increaseStock(100L, 2)
            verify(compensationEventLogRepository).save(argThat { compensationType == "PAYMENT_FAILED" })
        }

        @Test
        fun `이미_처리된_이벤트는_스킵한다`() {
            // given
            val event = PaymentFailedEvent(
                eventId = "payment-failed-1",
                paymentId = 1L,
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                reason = "잔액 부족"
            )

            whenever(compensationEventLogRepository.existsByEventId(event.eventId)).thenReturn(true)

            // when
            consumer.handlePaymentFailed(event)

            // then
            verify(orderRepository, never()).findById(any())
            verify(inventoryCommandService, never()).increaseStock(any(), any())
        }
    }

    @Nested
    @DisplayName("handlePaymentCancelled")
    inner class HandlePaymentCancelled {

        @Test
        fun `결제_취소_시_주문_취소_재고_복구_포인트_반환`() {
            // given
            val event = PaymentCancelledEvent(
                eventId = "payment-cancelled-1",
                paymentId = 1L,
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                transactionId = "TXN_123456"
            )

            val order = Order.paidFixture()
            val orderItem = OrderItem.fixture(orderId = 1L, productId = 100L, quantity = 2)
            val point = Point(userId = 1L, balance = BigDecimal("500")).withId(1L)
            val earnHistory = PointHistory(
                userId = 1L,
                amount = BigDecimal("500"),
                type = PointType.EARN,
                paymentId = 1L,
                eventId = "payment-1"
            ).withId(1L)

            whenever(compensationEventLogRepository.existsByEventId(event.eventId)).thenReturn(false)
            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(orderRepository.save(order)).thenReturn(order)
            whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))
            whenever(pointHistoryRepository.findByPaymentIdAndType(1L, PointType.EARN)).thenReturn(earnHistory)
            whenever(pointRepository.findByUserId(1L)).thenReturn(point)
            whenever(compensationEventLogRepository.save(any<CompensationEventLog>())).thenAnswer { it.arguments[0] }
            whenever(pointRepository.save(point)).thenReturn(point)
            whenever(pointHistoryRepository.save(any<PointHistory>())).thenAnswer { it.arguments[0] }

            // when
            consumer.handlePaymentCancelled(event)

            // then
            assertEquals(OrderStatus.CANCELLED, order.status)
            verify(orderRepository).save(order)
            verify(inventoryCommandService).increaseStock(100L, 2)
            assertEquals(BigDecimal.ZERO, point.balance)
            verify(pointHistoryRepository).save(argThat {
                type == PointType.REFUND && amount == BigDecimal("500")
            })
            verify(compensationEventLogRepository).save(argThat { compensationType == "PAYMENT_CANCELLED" })
        }

        @Test
        fun `이미_처리된_이벤트는_스킵한다`() {
            // given
            val event = PaymentCancelledEvent(
                eventId = "payment-cancelled-1",
                paymentId = 1L,
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                transactionId = "TXN_123456"
            )

            whenever(compensationEventLogRepository.existsByEventId(event.eventId)).thenReturn(true)

            // when
            consumer.handlePaymentCancelled(event)

            // then
            verify(orderRepository, never()).findById(any())
            verify(pointRepository, never()).findByUserId(any())
        }
    }
}
