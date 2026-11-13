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
import com.hoppingmall.payment.payment.exception.PaymentDirectCancelNotAllowedException
import com.hoppingmall.payment.payment.exception.PaymentDuplicateException
import com.hoppingmall.payment.payment.exception.PaymentInvalidAmountException
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.payment.exception.PaymentNotFoundException
import com.hoppingmall.payment.payment.exception.PaymentOrderNotFoundException
import com.hoppingmall.payment.payment.exception.PaymentOrderNotPayableException
import com.hoppingmall.payment.payment.exception.PaymentOrderUnavailableException
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.port.OrderSummary
import com.hoppingmall.payment.port.OrderSummaryQueryPort
import com.hoppingmall.payment.port.exception.OrderSummaryQueryFailedException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
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
    private val orderSummaryQueryPort: OrderSummaryQueryPort,
    transactionManager: PlatformTransactionManager
) : PaymentCommandService {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val log = LoggerFactory.getLogger(javaClass)

    override fun processPayment(paymentRequest: PaymentRequest, userId: Long): PaymentResponse = paymentMetrics.recordProcessingTime {
        verifyOrder(paymentRequest, userId)

        var couponDiscountAmount = BigDecimal.ZERO
        var couponId: Long? = null

        val savedPayment = try {
            transactionTemplate.execute {
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
        } catch (e: DataIntegrityViolationException) {
            log.warn("같은 주문의 결제가 이미 있어 결제 요청 거절: orderId={}, userId={}", paymentRequest.orderId, userId)
            throw PaymentDuplicateException()
        }

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

        if (payment.isCancellable() && !findOrder(payment.orderId).isAwaitingPayment()) {
            throw PaymentDirectCancelNotAllowedException()
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
        payment.updateStatus(
            newStatus = PaymentStatus.CANCELLED,
            transactionId = payment.transactionId,
            completedAt = payment.completedAt
        )
        val cancelledPayment = paymentRepository.save(payment)

        if (cancelledPayment.couponId != null) {
            couponCommandService.restoreCouponByOrder(cancelledPayment.orderId)
        }

        paymentMetrics.recordPaymentCancelled()
        paymentEventService.publishPaymentCancelledEvent(cancelledPayment)
        paymentNotificationService.publishPaymentCancelledNotification(cancelledPayment)

        log.info("결제 취소: paymentId={}, orderId={}, userId={}", payment.id, payment.orderId, payment.userId)
        return PaymentResponse.from(cancelledPayment)
    }

    private fun verifyOrder(paymentRequest: PaymentRequest, userId: Long) {
        val order = findOrder(paymentRequest.orderId)
        if (order.buyerId != userId) {
            throw PaymentAccessDeniedException()
        }
        if (!order.isAwaitingPayment()) {
            throw PaymentOrderNotPayableException()
        }
        if (order.totalAmount.compareTo(paymentRequest.amount) != 0) {
            throw PaymentInvalidAmountException()
        }
    }

    private fun findOrder(orderId: Long): OrderSummary {
        val order = try {
            orderSummaryQueryPort.findOrder(orderId)
        } catch (e: OrderSummaryQueryFailedException) {
            log.warn("주문 조회 실패로 결제 요청 거절: orderId={}", orderId)
            throw PaymentOrderUnavailableException()
        }
        return order ?: throw PaymentOrderNotFoundException()
    }

    private fun updatePaymentWithResult(payment: Payment, result: PaymentResult): Payment {
        if (result.isSuccess) {
            payment.updateStatus(
                newStatus = PaymentStatus.SUCCESS,
                transactionId = result.transactionId,
                completedAt = result.completedAt
            )
        } else {
            payment.updateStatus(
                newStatus = PaymentStatus.FAILED,
                errorMessage = result.errorMessage
            )
        }
        return payment
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
