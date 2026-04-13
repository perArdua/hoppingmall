package com.hoppingmall.payment.refund.service

import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.point.service.PointCommandService
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
import java.util.*

@DisplayName("RefundLocalOperationService")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundLocalOperationServiceTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var pointCommandService: PointCommandService

    @Mock
    private lateinit var couponCommandService: CouponCommandService

    @Mock
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @InjectMocks
    private lateinit var service: RefundLocalOperationService

    private fun fullRefundEvent(couponId: Long? = 10L) = RefundCompletedEvent(
        eventId = "evt-1",
        refundId = 1L,
        orderId = 100L,
        paymentId = 200L,
        buyerId = 300L,
        refundAmount = BigDecimal("50000"),
        pointRefundAmount = BigDecimal("1000"),
        isFullRefund = true,
        couponId = couponId,
        items = listOf(RefundItemEvent(productId = 1L, quantity = 2, refundPrice = BigDecimal("25000")))
    )

    private fun partialRefundEvent() = RefundCompletedEvent(
        eventId = "evt-2",
        refundId = 2L,
        orderId = 100L,
        paymentId = 200L,
        buyerId = 300L,
        refundAmount = BigDecimal("25000"),
        pointRefundAmount = BigDecimal("500"),
        isFullRefund = false,
        couponId = null,
        items = listOf(RefundItemEvent(productId = 1L, quantity = 1, refundPrice = BigDecimal("25000")))
    )

    private fun newEventLog() = RefundEventLog(
        eventId = "evt-1",
        eventType = "REFUND_COMPLETED",
        refundId = 1L,
        orderId = 100L
    )

    @Test
    fun 전체_환불_시_결제상태_쿠폰_포인트_처리() {
        val event = fullRefundEvent()
        val eventLog = newEventLog()
        val payment = Payment.create(
            orderId = 100L,
            userId = 300L,
            amount = BigDecimal("50000"),
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(newStatus = PaymentStatus.SUCCESS, transactionId = "txn-1")

        whenever(paymentRepository.findById(event.paymentId)).thenReturn(Optional.of(payment))
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(eventLog)

        service.execute(event, eventLog)

        verify(paymentRepository).findById(event.paymentId)
        verify(paymentRepository).save(payment)
        verify(couponCommandService).restoreCouponByOrder(event.orderId)
        verify(pointCommandService).refundPoints(
            userId = event.buyerId,
            amount = event.pointRefundAmount,
            paymentId = event.paymentId,
            orderId = event.orderId
        )
        verify(refundEventLogRepository).save(eventLog)
    }

    @Test
    fun 부분_환불_시_결제상태와_쿠폰은_처리하지_않음() {
        val event = partialRefundEvent()
        val eventLog = RefundEventLog(
            eventId = "evt-2",
            eventType = "REFUND_COMPLETED",
            refundId = 2L,
            orderId = 100L
        )

        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(eventLog)

        service.execute(event, eventLog)

        verify(paymentRepository, never()).findById(any())
        verify(couponCommandService, never()).restoreCouponByOrder(any())
        verify(pointCommandService).refundPoints(
            userId = event.buyerId,
            amount = event.pointRefundAmount,
            paymentId = event.paymentId,
            orderId = event.orderId
        )
        verify(refundEventLogRepository).save(eventLog)
    }

    @Test
    fun 이미_완료된_스텝은_재처리하지_않음() {
        val event = fullRefundEvent()
        val eventLog = newEventLog()
        eventLog.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
        eventLog.markStepCompleted(RefundEventLog.COUPON_RESTORED)
        eventLog.markStepCompleted(RefundEventLog.POINTS_REFUNDED)

        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(eventLog)

        service.execute(event, eventLog)

        verify(paymentRepository, never()).findById(any())
        verify(couponCommandService, never()).restoreCouponByOrder(any())
        verify(pointCommandService, never()).refundPoints(any(), any(), any(), any())
        verify(refundEventLogRepository).save(eventLog)
    }
}
