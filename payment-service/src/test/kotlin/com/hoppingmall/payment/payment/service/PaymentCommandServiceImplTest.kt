package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentAccessDeniedException
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.payment.exception.PaymentNotFoundException
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.common.BaseEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal

@DisplayName("PaymentCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCommandServiceImplTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var paymentEventService: PaymentEventService

    @Mock
    private lateinit var paymentNotificationService: PaymentNotificationService

    @Mock
    private lateinit var couponCommandService: CouponCommandService

    @Mock
    private lateinit var paymentMetrics: PaymentMetrics

    @Mock
    private lateinit var pointEarnRateStrategy: PointEarnRateStrategy

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private fun createService() = PaymentCommandServiceImpl(
        paymentRepository,
        paymentService,
        paymentEventService,
        paymentNotificationService,
        couponCommandService,
        paymentMetrics,
        pointEarnRateStrategy,
        transactionManager
    )

    private fun createPayment(
        id: Long = 1L,
        status: PaymentStatus = PaymentStatus.SUCCESS,
        userId: Long = 10L
    ): Payment {
        val payment = Payment.create(
            orderId = 100L,
            userId = userId,
            amount = BigDecimal("50000"),
            method = PaymentMethod.CREDIT_CARD
        )
        if (status != PaymentStatus.PENDING) {
            payment.updateStatus(status, transactionId = "test-tx-${System.nanoTime()}")
        }
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, id)
        return payment
    }

    @Test
    fun cancelPayment_성공_결제를_취소한다() {
        val service = createService()
        val payment = createPayment(status = PaymentStatus.SUCCESS, userId = 10L)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        val result = service.cancelPayment(1L, 10L)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(paymentMetrics).recordPaymentCancelled()
        verify(paymentEventService).publishPaymentCancelledEvent(any())
        verify(paymentNotificationService).publishPaymentCancelledNotification(any())
    }

    @Test
    fun cancelPayment_다른_사용자면_접근_거부() {
        val service = createService()
        val payment = createPayment(status = PaymentStatus.SUCCESS, userId = 10L)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)

        assertThatThrownBy { service.cancelPayment(1L, 999L) }
            .isInstanceOf(PaymentAccessDeniedException::class.java)
    }

    @Test
    fun cancelPayment_결제_없으면_예외() {
        val service = createService()
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(null)

        assertThatThrownBy { service.cancelPayment(1L, 10L) }
            .isInstanceOf(PaymentNotFoundException::class.java)
    }

    @Test
    fun cancelPayment_취소_불가_상태면_예외() {
        val service = createService()
        val payment = createPayment(status = PaymentStatus.CANCELLED)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)

        assertThatThrownBy { service.cancelPayment(1L, 10L) }
            .isInstanceOf(PaymentInvalidStateException::class.java)
    }

    @Test
    fun cancelPaymentInternal_userId_없이_결제를_취소한다() {
        val service = createService()
        val payment = createPayment(status = PaymentStatus.SUCCESS, userId = 10L)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        val result = service.cancelPaymentInternal(1L)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(paymentMetrics).recordPaymentCancelled()
        verify(paymentEventService).publishPaymentCancelledEvent(any())
    }

    @Test
    fun cancelPaymentInternal_타인_결제도_취소_가능() {
        val service = createService()
        val payment = createPayment(status = PaymentStatus.SUCCESS, userId = 999L)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        val result = service.cancelPaymentInternal(1L)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
    }

    @Test
    fun cancelPaymentInternal_PENDING_결제도_취소_가능() {
        val service = createService()
        val payment = createPayment(status = PaymentStatus.PENDING)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        val result = service.cancelPaymentInternal(1L)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
    }

    @Test
    fun cancelPaymentInternal_취소_불가_상태면_예외() {
        val service = createService()
        val payment = createPayment(status = PaymentStatus.REFUNDED)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)

        assertThatThrownBy { service.cancelPaymentInternal(1L) }
            .isInstanceOf(PaymentInvalidStateException::class.java)

        verify(paymentRepository, never()).save(any<Payment>())
    }

    @Test
    fun cancelPaymentInternal_결제_없으면_예외() {
        val service = createService()
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(null)

        assertThatThrownBy { service.cancelPaymentInternal(1L) }
            .isInstanceOf(PaymentNotFoundException::class.java)
    }
}
