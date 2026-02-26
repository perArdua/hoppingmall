package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.coupon.service.CouponCommandService
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.point.service.PointCommandService
import com.hoppingmall.mall.product.service.ProductStatisticsCommandService
import com.hoppingmall.mall.refund.domain.RefundEventLog
import com.hoppingmall.mall.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.mall.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.mall.refund.dto.event.RefundItemEvent
import com.hoppingmall.mall.support.fixture.paidFixture
import com.hoppingmall.mall.support.fixture.successFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.*

@DisplayName("RefundCompletionConsumer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundCompletionConsumerTest {

    private val refundEventLogRepository: RefundEventLogRepository = mock()
    private val inventoryCommandService: InventoryCommandService = mock()
    private val pointCommandService: PointCommandService = mock()
    private val productStatisticsCommandService: ProductStatisticsCommandService = mock()
    private val couponCommandService: CouponCommandService = mock()
    private val paymentRepository: PaymentRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()

    private val consumer = RefundCompletionConsumer(
        refundEventLogRepository,
        inventoryCommandService,
        pointCommandService,
        productStatisticsCommandService,
        couponCommandService,
        paymentRepository,
        orderRepository,
        objectMapper
    )

    @Test
    fun `환불_완료_이벤트_정상_처리`() {
        // given
        val event = RefundCompletedEvent(
            eventId = "test-event-1",
            refundId = 1L,
            orderId = 1L,
            paymentId = 1L,
            buyerId = 1L,
            refundAmount = BigDecimal("30000"),
            pointRefundAmount = BigDecimal("1000"),
            isFullRefund = true,
            items = listOf(
                RefundItemEvent(productId = 100L, quantity = 2, refundPrice = BigDecimal("30000"))
            )
        )

        val payment = Payment.successFixture(orderId = 1L)
        val order = Order.paidFixture(buyerId = 1L)

        whenever(refundEventLogRepository.existsByEventId(event.eventId)).thenReturn(false)
        whenever(paymentRepository.findById(1L)).thenReturn(Optional.of(payment))
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        // when
        consumer.processRefundCompletion(event)

        // then
        verify(inventoryCommandService).increaseStock(100L, 2)
        verify(pointCommandService).refundPoints(eq(1L), eq(BigDecimal("1000")), eq(1L), eq(1L))
        verify(productStatisticsCommandService).incrementRefundStats(eq(100L), eq(2L), eq(BigDecimal("30000")))
        verify(paymentRepository).save(any())
        verify(orderRepository).save(any())
        verify(refundEventLogRepository).save(any<RefundEventLog>())
    }

    @Test
    fun `이미_처리된_이벤트는_스킵`() {
        // given
        val event = RefundCompletedEvent(
            eventId = "already-processed",
            refundId = 1L,
            orderId = 1L,
            paymentId = 1L,
            buyerId = 1L,
            refundAmount = BigDecimal("30000"),
            pointRefundAmount = BigDecimal("1000"),
            isFullRefund = true,
            items = listOf(
                RefundItemEvent(productId = 100L, quantity = 2, refundPrice = BigDecimal("30000"))
            )
        )

        whenever(refundEventLogRepository.existsByEventId(event.eventId)).thenReturn(true)

        // when
        consumer.processRefundCompletion(event)

        // then
        verify(inventoryCommandService, never()).increaseStock(any(), any())
        verify(pointCommandService, never()).refundPoints(any(), any(), any(), any())
        verify(productStatisticsCommandService, never()).incrementRefundStats(any(), any(), any())
        verify(refundEventLogRepository, never()).save(any<RefundEventLog>())
    }

    @Test
    fun `부분_환불_시_결제_주문_상태_미변경`() {
        // given
        val event = RefundCompletedEvent(
            eventId = "partial-refund-event",
            refundId = 1L,
            orderId = 1L,
            paymentId = 1L,
            buyerId = 1L,
            refundAmount = BigDecimal("15000"),
            pointRefundAmount = BigDecimal("500"),
            isFullRefund = false,
            items = listOf(
                RefundItemEvent(productId = 100L, quantity = 1, refundPrice = BigDecimal("15000"))
            )
        )

        whenever(refundEventLogRepository.existsByEventId(event.eventId)).thenReturn(false)
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        // when
        consumer.processRefundCompletion(event)

        // then
        verify(inventoryCommandService).increaseStock(100L, 1)
        verify(pointCommandService).refundPoints(eq(1L), eq(BigDecimal("500")), eq(1L), eq(1L))
        verify(productStatisticsCommandService).incrementRefundStats(eq(100L), eq(1L), eq(BigDecimal("15000")))
        verify(paymentRepository, never()).findById(any())
        verify(paymentRepository, never()).save(any())
        verify(orderRepository, never()).findById(any())
        verify(orderRepository, never()).save(any())
    }
}
