package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.dto.event.PaymentFailedEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
    fun 결제_실패_시_보상_완료_기록만_수행한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(pendingLog())

        consumer.handlePaymentFailed(failedEvent)

        verify(compensationEventLogService).markCompleted("evt-1")
    }

    @Test
    fun 이미_완료된_보상_이벤트는_스킵한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(completedLog())

        consumer.handlePaymentFailed(failedEvent)

        verify(compensationEventLogService, never()).markCompleted(any())
    }

    @Test
    fun 결제_취소_시_포인트_환불만_수행한다() {
        val cancelLog = CompensationEventLog(
            eventId = "evt-2",
            compensationType = "PAYMENT_CANCELLED",
            paymentId = 100L,
            orderId = 200L,
            status = CompensationEventLogStatus.PENDING
        )
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(cancelLog)

        consumer.handlePaymentCancelled(cancelledEvent)

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

        verify(refundPointsService, never()).refundPoints(any(), any())
    }

    @Test
    fun 결제_역보상_요청_시_포인트_환불을_수행한다() {
        val node = mock<com.fasterxml.jackson.databind.JsonNode>()
        whenever(objectMapper.readTree(any<String>())).thenReturn(node)
        whenever(node.get("eventType")).thenReturn(TextNode("PaymentReversalRequested"))
        whenever(node.get("eventId")).thenReturn(TextNode("reversal-evt-1"))
        whenever(node.get("orderId")).thenReturn(LongNode(200L))
        whenever(node.get("paymentId")).thenReturn(LongNode(100L))
        whenever(node.get("userId")).thenReturn(LongNode(1L))

        val pendingReversalLog = CompensationEventLog(
            eventId = "reversal-evt-1",
            compensationType = "PAYMENT_REVERSAL",
            paymentId = 100L,
            orderId = 200L,
            status = CompensationEventLogStatus.PENDING
        )
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(pendingReversalLog)

        consumer.handlePaymentReversal("{}")

        verify(refundPointsService).refundPoints(1L, 100L)
        verify(compensationEventLogService).markCompleted("reversal-evt-1")
    }

    @Test
    fun 이미_처리된_역보상_이벤트는_스킵한다() {
        val node = mock<com.fasterxml.jackson.databind.JsonNode>()
        whenever(objectMapper.readTree(any<String>())).thenReturn(node)
        whenever(node.get("eventType")).thenReturn(TextNode("PaymentReversalRequested"))
        whenever(node.get("eventId")).thenReturn(TextNode("reversal-evt-1"))
        whenever(node.get("orderId")).thenReturn(LongNode(200L))
        whenever(node.get("paymentId")).thenReturn(LongNode(100L))
        whenever(node.get("userId")).thenReturn(LongNode(1L))

        val completedReversalLog = CompensationEventLog(
            eventId = "reversal-evt-1",
            compensationType = "PAYMENT_REVERSAL",
            paymentId = 100L,
            orderId = 200L,
            status = CompensationEventLogStatus.COMPLETED
        )
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(completedReversalLog)

        consumer.handlePaymentReversal("{}")

        verify(refundPointsService, never()).refundPoints(any(), any())
    }
}
