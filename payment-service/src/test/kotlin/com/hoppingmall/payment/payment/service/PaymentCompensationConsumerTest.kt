package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import com.hoppingmall.payment.port.InventoryCommandPort
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.port.OrderItemInfo
import com.hoppingmall.payment.port.OrderQueryPort
import com.hoppingmall.payment.port.exception.OrderCancellationFailedException
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("PaymentCompensationConsumer")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCompensationConsumerTest {

    @Mock
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Mock
    private lateinit var orderCommandPort: OrderCommandPort

    @Mock
    private lateinit var orderQueryPort: OrderQueryPort

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var refundPointsService: RefundPointsService

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var consumer: PaymentCompensationConsumer

    private fun pendingLog(eventId: String = "evt-1") = CompensationEventLog(
        eventId = eventId,
        compensationType = "PAYMENT_FAILED",
        paymentId = 100L,
        orderId = 200L,
        status = CompensationEventLogStatus.PENDING
    )

    private fun completedLog(eventId: String = "evt-1") = CompensationEventLog(
        eventId = eventId,
        compensationType = "PAYMENT_FAILED",
        paymentId = 100L,
        orderId = 200L,
        status = CompensationEventLogStatus.COMPLETED
    )

    private val failedEvent = PaymentFailedEvent(
        eventId = "evt-1",
        paymentId = 100L,
        orderId = 200L,
        userId = 1L,
        amount = BigDecimal("50000"),
        reason = "잔액 부족"
    )

    private val cancelledEvent = PaymentCancelledEvent(
        eventId = "evt-2",
        paymentId = 100L,
        orderId = 200L,
        userId = 1L,
        amount = BigDecimal("50000"),
        transactionId = "txn-1"
    )

    @Test
    fun 결제_실패_시_주문_취소_및_재고_복구를_수행한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(pendingLog())
        whenever(orderCommandPort.cancelOrder(200L)).thenReturn(true)
        whenever(orderQueryPort.findOrderItemsByOrderId(200L))
            .thenReturn(listOf(OrderItemInfo(1L, 10L, 2, BigDecimal("20000"))))

        consumer.handlePaymentFailed(failedEvent)

        verify(orderCommandPort).cancelOrder(200L)
        verify(inventoryCommandPort).increaseStock(10L, 2)
        verify(compensationEventLogService).markCompleted("evt-1")
    }

    @Test
    fun 이미_완료된_보상_이벤트는_스킵한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(completedLog())

        consumer.handlePaymentFailed(failedEvent)

        verify(orderCommandPort, never()).cancelOrder(any())
        verify(inventoryCommandPort, never()).increaseStock(any(), any())
        verify(compensationEventLogService, never()).markCompleted(any())
    }

    @Test
    fun 주문이_이미_취소되어도_재고_복구는_계속_수행한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(pendingLog())
        whenever(orderCommandPort.cancelOrder(200L)).thenReturn(false)
        whenever(orderQueryPort.findOrderItemsByOrderId(200L))
            .thenReturn(listOf(OrderItemInfo(1L, 10L, 2, BigDecimal("20000"))))

        consumer.handlePaymentFailed(failedEvent)

        verify(inventoryCommandPort).increaseStock(10L, 2)
        verify(compensationEventLogService).markCompleted("evt-1")
    }

    @Test
    fun 주문_취소_실패_시_예외가_전파된다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(pendingLog())
        whenever(orderCommandPort.cancelOrder(200L))
            .thenThrow(OrderCancellationFailedException(200L, RuntimeException("Connection refused")))

        assertThatThrownBy { consumer.handlePaymentFailed(failedEvent) }
            .isInstanceOf(OrderCancellationFailedException::class.java)

        verify(compensationEventLogService, never()).markCompleted(any())
    }

    @Test
    fun 결제_취소_시_주문_취소_재고_복구_포인트_환불을_수행한다() {
        val cancelLog = CompensationEventLog(
            eventId = "evt-2",
            compensationType = "PAYMENT_CANCELLED",
            paymentId = 100L,
            orderId = 200L,
            status = CompensationEventLogStatus.PENDING
        )
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(cancelLog)
        whenever(orderCommandPort.cancelOrder(200L)).thenReturn(true)
        whenever(orderQueryPort.findOrderItemsByOrderId(200L))
            .thenReturn(listOf(OrderItemInfo(1L, 10L, 2, BigDecimal("20000"))))

        consumer.handlePaymentCancelled(cancelledEvent)

        verify(orderCommandPort).cancelOrder(200L)
        verify(inventoryCommandPort).increaseStock(10L, 2)
        verify(refundPointsService).refundPoints(1L, 100L)
        verify(compensationEventLogService).markCompleted("evt-2")
    }

    @Test
    fun 결제_취소_이미_완료된_이벤트는_스킵한다() {
        val completedCancelLog = CompensationEventLog(
            eventId = "evt-2",
            compensationType = "PAYMENT_CANCELLED",
            paymentId = 100L,
            orderId = 200L,
            status = CompensationEventLogStatus.COMPLETED
        )
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(completedCancelLog)

        consumer.handlePaymentCancelled(cancelledEvent)

        verify(orderCommandPort, never()).cancelOrder(any())
        verify(refundPointsService, never()).refundPoints(any(), any())
    }
}
