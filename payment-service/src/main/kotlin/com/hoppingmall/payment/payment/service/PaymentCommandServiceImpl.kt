package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.PaymentResult
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.exception.PaymentAccessDeniedException
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.payment.exception.PaymentNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class PaymentCommandServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val paymentEventService: PaymentEventService,
    private val couponCommandService: CouponCommandService
) : PaymentCommandService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun processPayment(paymentRequest: PaymentRequest, userId: Long): PaymentResponse {
        var couponDiscountAmount = BigDecimal.ZERO
        var couponId: Long? = null

        if (paymentRequest.couponId != null) {
            couponDiscountAmount = couponCommandService.useCoupon(
                userId = userId,
                couponId = paymentRequest.couponId,
                orderAmount = paymentRequest.amount,
                orderId = paymentRequest.orderId
            )
            couponId = paymentRequest.couponId
        }

        val payment = Payment.create(
            orderId = paymentRequest.orderId,
            userId = userId,
            amount = paymentRequest.amount.subtract(couponDiscountAmount),
            method = paymentRequest.method,
            pointAmount = paymentRequest.pointAmount,
            couponId = couponId,
            couponDiscountAmount = couponDiscountAmount
        )

        val savedPayment = paymentRepository.save(payment)

        val paymentResult = paymentService.processPayment(savedPayment)

        val updatedPayment = updatePaymentWithResult(savedPayment, paymentResult)

        val finalPayment = paymentRepository.save(updatedPayment)

        if (finalPayment.status == PaymentStatus.SUCCESS) {
            log.info("결제 성공: paymentId={}, orderId={}, userId={}, amount={}", finalPayment.id, finalPayment.orderId, userId, finalPayment.amount)
            publishPaymentEvents(finalPayment)
        }

        if (finalPayment.status == PaymentStatus.FAILED) {
            log.warn("결제 실패: paymentId={}, orderId={}, userId={}, error={}", finalPayment.id, finalPayment.orderId, userId, finalPayment.errorMessage)
            if (couponId != null) {
                couponCommandService.restoreCouponByPayment(couponId, userId)
            }
            paymentEventService.publishPaymentFailedEvent(finalPayment)
            paymentEventService.publishPaymentFailedNotification(finalPayment)
        }

        return PaymentResponse.from(finalPayment)
    }

    override fun cancelPayment(paymentId: Long, userId: Long): PaymentResponse {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException() }

        if (payment.userId != userId) {
            throw PaymentAccessDeniedException()
        }

        if (payment.status != PaymentStatus.SUCCESS) {
            throw PaymentInvalidStateException()
        }

        payment.status = PaymentStatus.CANCELLED
        val cancelledPayment = paymentRepository.save(payment)

        paymentEventService.publishPaymentCancelledEvent(cancelledPayment)
        paymentEventService.publishPaymentCancelledNotification(cancelledPayment)

        log.info("결제 취소: paymentId={}, orderId={}, userId={}", paymentId, payment.orderId, userId)
        return PaymentResponse.from(cancelledPayment)
    }

    private fun updatePaymentWithResult(payment: Payment, result: PaymentResult): Payment {
        return payment.apply {
            if (result.isSuccess) {
                status = PaymentStatus.SUCCESS
                transactionId = result.transactionId
                completedAt = result.completedAt
            } else {
                status = PaymentStatus.FAILED
                errorMessage = result.errorMessage
            }
        }
    }

    private fun publishPaymentEvents(payment: Payment) {
        paymentEventService.publishPaymentCompletedEvent(payment)

        if (payment.amount > BigDecimal.ZERO) {
            paymentEventService.publishPointEarnRequestEvent(payment)
            paymentEventService.publishMembershipUpdateEvent(payment)
        }
        paymentEventService.publishPaymentCompletedNotification(payment)
    }
}
