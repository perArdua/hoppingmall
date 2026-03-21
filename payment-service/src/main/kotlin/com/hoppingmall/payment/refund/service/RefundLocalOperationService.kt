package com.hoppingmall.payment.refund.service

import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.point.service.PointCommandService
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.payment.refund.dto.event.RefundCompletedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefundLocalOperationService(
    private val paymentRepository: PaymentRepository,
    private val pointCommandService: PointCommandService,
    private val couponCommandService: CouponCommandService,
    private val refundEventLogRepository: RefundEventLogRepository
) {

    @Transactional
    fun execute(event: RefundCompletedEvent, eventLog: RefundEventLog) {
        if (event.isFullRefund) {
            if (!eventLog.isStepCompleted(RefundEventLog.PAYMENT_UPDATED)) {
                val payment = paymentRepository.findById(event.paymentId).orElse(null)
                if (payment != null) {
                    payment.updateStatus(PaymentStatus.REFUNDED)
                    paymentRepository.save(payment)
                }
                eventLog.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
            }

            if (!eventLog.isStepCompleted(RefundEventLog.COUPON_RESTORED) && event.couponId != null) {
                couponCommandService.restoreCouponByOrder(event.orderId)
                eventLog.markStepCompleted(RefundEventLog.COUPON_RESTORED)
            }
        } else {
            eventLog.markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
            eventLog.markStepCompleted(RefundEventLog.COUPON_RESTORED)
        }

        if (!eventLog.isStepCompleted(RefundEventLog.POINTS_REFUNDED)) {
            pointCommandService.refundPoints(
                userId = event.buyerId,
                amount = event.pointRefundAmount,
                paymentId = event.paymentId,
                orderId = event.orderId
            )
            eventLog.markStepCompleted(RefundEventLog.POINTS_REFUNDED)
        }

        refundEventLogRepository.save(eventLog)
    }
}
