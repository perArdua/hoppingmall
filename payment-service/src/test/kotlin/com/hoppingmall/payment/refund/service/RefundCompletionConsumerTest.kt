package com.hoppingmall.payment.refund.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.port.InventoryCommandPort
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.port.ProductStatisticsPort
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.payment.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.payment.refund.dto.event.RefundItemEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.math.BigDecimal

@DisplayName("RefundCompletionConsumer")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundCompletionConsumerTest {

    @Mock
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @Mock
    private lateinit var refundLocalOperationService: RefundLocalOperationService

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var orderCommandPort: OrderCommandPort

    @Mock
    private lateinit var productStatisticsPort: ProductStatisticsPort

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var consumer: RefundCompletionConsumer

    private fun fullRefundEvent(
        eventId: String = "evt-refund-1",
        couponId: Long? = 10L
    ) = RefundCompletedEvent(
        eventId = eventId,
        refundId = 1L,
        orderId = 100L,
        paymentId = 200L,
        buyerId = 300L,
        refundAmount = BigDecimal("50000"),
        pointRefundAmount = BigDecimal("1000"),
        isFullRefund = true,
        couponId = couponId,
        items = listOf(
            RefundItemEvent(productId = 1L, quantity = 2, refundPrice = BigDecimal("25000")),
            RefundItemEvent(productId = 2L, quantity = 1, refundPrice = BigDecimal("25000"))
        )
    )

    private fun partialRefundEvent(eventId: String = "evt-refund-2") = RefundCompletedEvent(
        eventId = eventId,
        refundId = 2L,
        orderId = 100L,
        paymentId = 200L,
        buyerId = 300L,
        refundAmount = BigDecimal("25000"),
        pointRefundAmount = BigDecimal("500"),
        isFullRefund = false,
        couponId = null,
        items = listOf(
            RefundItemEvent(productId = 1L, quantity = 1, refundPrice = BigDecimal("25000"))
        )
    )

    private fun newEventLog(eventId: String = "evt-refund-1") = RefundEventLog(
        eventId = eventId,
        eventType = "REFUND_COMPLETED",
        refundId = 1L,
        orderId = 100L
    )

    private fun completedEventLog(eventId: String = "evt-refund-1"): RefundEventLog {
        val log = RefundEventLog(
            eventId = eventId,
            eventType = "REFUND_COMPLETED",
            refundId = 1L,
            orderId = 100L
        )
        log.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
        log.markStepCompleted(RefundEventLog.POINTS_REFUNDED)
        log.markStepCompleted(RefundEventLog.COUPON_RESTORED)
        log.markStepCompleted(RefundEventLog.INVENTORY_RESTORED)
        log.markStepCompleted(RefundEventLog.STATS_UPDATED)
        log.markStepCompleted(RefundEventLog.ORDER_CANCELLED)
        return log
    }

    @Test
    fun 전체_환불_완료_처리_성공() {
        val event = fullRefundEvent()
        val eventLog = newEventLog()

        whenever(refundEventLogRepository.findByEventIdWithSteps(event.eventId)).thenReturn(null)
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(eventLog)
        whenever(orderCommandPort.cancelOrder(event.orderId)).thenReturn(true)

        consumer.processRefundCompletion(event)

        verify(refundLocalOperationService).execute(eq(event), any())
        verify(inventoryCommandPort).increaseStock(1L, 2)
        verify(inventoryCommandPort).increaseStock(2L, 1)
        verify(productStatisticsPort).incrementRefundStats(1L, 2L, BigDecimal("25000"))
        verify(productStatisticsPort).incrementRefundStats(2L, 1L, BigDecimal("25000"))
        verify(orderCommandPort).cancelOrder(event.orderId)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.INVENTORY_RESTORED)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.STATS_UPDATED)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.ORDER_CANCELLED)
    }

    @Test
    fun 부분_환불_완료_처리_성공() {
        val event = partialRefundEvent()
        val eventLog = newEventLog(event.eventId)

        whenever(refundEventLogRepository.findByEventIdWithSteps(event.eventId)).thenReturn(null)
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(eventLog)

        consumer.processRefundCompletion(event)

        verify(refundLocalOperationService).execute(eq(event), any())
        verify(inventoryCommandPort).increaseStock(1L, 1)
        verify(productStatisticsPort).incrementRefundStats(1L, 1L, BigDecimal("25000"))
        verify(orderCommandPort, never()).cancelOrder(any())
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.INVENTORY_RESTORED)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.STATS_UPDATED)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.ORDER_CANCELLED)
    }

    @Test
    fun 이미_처리된_이벤트는_건너뛴다() {
        val event = fullRefundEvent()
        val completedLog = completedEventLog()

        whenever(refundEventLogRepository.findByEventIdWithSteps(event.eventId)).thenReturn(completedLog)

        consumer.processRefundCompletion(event)

        verify(refundEventLogRepository, never()).save(any<RefundEventLog>())
        verify(refundLocalOperationService, never()).execute(any(), any())
        verify(refundLocalOperationService, never()).markStepAndSave(any(), any())
        verify(inventoryCommandPort, never()).increaseStock(any(), any())
        verify(orderCommandPort, never()).cancelOrder(any())
    }

    @Test
    fun 로컬_작업_완료_후_재시도_시_원격_작업만_수행() {
        val event = fullRefundEvent()
        val partialLog = newEventLog()
        partialLog.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
        partialLog.markStepCompleted(RefundEventLog.COUPON_RESTORED)
        partialLog.markStepCompleted(RefundEventLog.POINTS_REFUNDED)

        whenever(refundEventLogRepository.findByEventIdWithSteps(event.eventId)).thenReturn(partialLog)
        whenever(orderCommandPort.cancelOrder(event.orderId)).thenReturn(true)

        consumer.processRefundCompletion(event)

        verify(refundLocalOperationService).execute(eq(event), any())
        verify(inventoryCommandPort).increaseStock(1L, 2)
        verify(inventoryCommandPort).increaseStock(2L, 1)
        verify(productStatisticsPort).incrementRefundStats(1L, 2L, BigDecimal("25000"))
        verify(productStatisticsPort).incrementRefundStats(2L, 1L, BigDecimal("25000"))
        verify(orderCommandPort).cancelOrder(event.orderId)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.INVENTORY_RESTORED)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.STATS_UPDATED)
        verify(refundLocalOperationService).markStepAndSave(event.eventId, RefundEventLog.ORDER_CANCELLED)
    }
}
