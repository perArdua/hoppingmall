package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.config.PaymentMetrics
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
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@Service
class PaymentCommandServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val paymentEventService: PaymentEventService,
    private val paymentNotificationService: PaymentNotificationService,
    private val couponCommandService: CouponCommandService,
    private val paymentMetrics: PaymentMetrics,
    private val pointEarnRateStrategy: PointEarnRateStrategy,
    transactionManager: PlatformTransactionManager
) : PaymentCommandService {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val log = LoggerFactory.getLogger(javaClass)

    override fun processPayment(paymentRequest: PaymentRequest, userId: Long): PaymentResponse = paymentMetrics.recordProcessingTime {
        var couponDiscountAmount = BigDecimal.ZERO
        var couponId: Long? = null

        val savedPayment = transactionTemplate.execute {
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

            paymentRepository.save(payment)
        }!!

        val paymentResult = paymentService.processPayment(savedPayment)

        val earnRate = if (savedPayment.amount > BigDecimal.ZERO) {
            pointEarnRateStrategy.getEarnRate(savedPayment.userId)
        } else null

        val finalPayment = transactionTemplate.execute {
            val updatedPayment = updatePaymentWithResult(savedPayment, paymentResult)
            val saved = paymentRepository.save(updatedPayment)

            if (saved.status == PaymentStatus.SUCCESS) {
                log.info("결제 성공: paymentId={}, orderId={}, userId={}, amount={}", saved.id, saved.orderId, userId, saved.amount)
                paymentMetrics.recordPaymentCompleted(saved.amount, saved.method.name)
                publishPaymentEvents(saved, earnRate)
            }

            if (saved.status == PaymentStatus.FAILED) {
                log.warn("결제 실패: paymentId={}, orderId={}, userId={}, error={}", saved.id, saved.orderId, userId, saved.errorMessage)
                paymentMetrics.recordPaymentFailed()
                if (couponId != null) {
                    couponCommandService.restoreCouponByPayment(couponId!!, userId)
                }
                paymentEventService.publishPaymentFailedEvent(saved)
                paymentNotificationService.publishPaymentFailedNotification(saved)
            }

            saved
        }!!

        PaymentResponse.from(finalPayment)
    }

    @Transactional
    override fun cancelPayment(paymentId: Long, userId: Long): PaymentResponse {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw PaymentNotFoundException()

        if (payment.userId != userId) {
            throw PaymentAccessDeniedException()
        }

        return executeCancelPayment(payment)
    }

    @Transactional
    override fun cancelPaymentInternal(paymentId: Long): PaymentResponse {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw PaymentNotFoundException()

        return executeCancelPayment(payment)
    }

    private fun executeCancelPayment(payment: Payment): PaymentResponse {
        if (payment.status != PaymentStatus.SUCCESS && payment.status != PaymentStatus.PENDING) {
            throw PaymentInvalidStateException()
        }

        payment.status = PaymentStatus.CANCELLED
        val cancelledPayment = paymentRepository.save(payment)

        paymentMetrics.recordPaymentCancelled()
        paymentEventService.publishPaymentCancelledEvent(cancelledPayment)
        paymentNotificationService.publishPaymentCancelledNotification(cancelledPayment)

        log.info("결제 취소: paymentId={}, orderId={}, userId={}", payment.id, payment.orderId, payment.userId)
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

    private fun publishPaymentEvents(payment: Payment, earnRate: BigDecimal? = null) {
        paymentEventService.publishPaymentCompletedEvent(payment)

        if (payment.amount > BigDecimal.ZERO && earnRate != null) {
            paymentEventService.publishPointEarnRequestEvent(payment, earnRate)
            paymentEventService.publishMembershipUpdateEvent(payment)
        }
        paymentNotificationService.publishPaymentCompletedNotification(payment)
    }
}
