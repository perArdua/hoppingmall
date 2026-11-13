package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentDirectCancelNotAllowedException
import com.hoppingmall.payment.payment.exception.PaymentException
import com.hoppingmall.payment.payment.exception.PaymentOrderUnavailableException
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("결제 직접 취소의 주문 상태 확인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentDirectCancelOrderStatusTest {

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

    private val orderSummaryQueryPort = FakeOrderSummaryQueryPort()

    private fun service() = PaymentCommandServiceImpl(
        paymentRepository,
        paymentService,
        paymentEventService,
        paymentNotificationService,
        couponCommandService,
        paymentMetrics,
        pointEarnRateStrategy,
        orderSummaryQueryPort,
        transactionManager
    )

    private fun successPayment(): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = BUYER_ID,
            amount = BigDecimal("30000"),
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(
            newStatus = PaymentStatus.SUCCESS,
            transactionId = "tx-direct-cancel",
            completedAt = LocalDateTime.of(2026, 11, 13, 10, 0, 0)
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, PAYMENT_ID)
        return payment
    }

    @Test
    fun 결제_대기_주문의_결제는_구매자가_직접_취소할_수_있다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, BigDecimal("30000"), status = "PAYING")
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(successPayment())
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        val result = service().cancelPayment(PAYMENT_ID, BUYER_ID)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(paymentEventService).publishPaymentCancelledEvent(any())
    }

    @ParameterizedTest
    @ValueSource(strings = ["PAID", "SHIPPED", "DELIVERED", "CANCEL_REQUESTED"])
    fun 결제_대기가_지난_주문의_결제_직접_취소는_400으로_거절하고_결제를_그대로_둔다(orderStatus: String) {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, BigDecimal("30000"), status = orderStatus)
        val payment = successPayment()
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(payment)
        Mockito.lenient().`when`(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        assertThatThrownBy { service().cancelPayment(PAYMENT_ID, BUYER_ID) }
            .isInstanceOf(PaymentDirectCancelNotAllowedException::class.java)
            .satisfies({ assertThat((it as PaymentException).errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST) })
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        verify(paymentEventService, never()).publishPaymentCancelledEvent(any())
        verify(couponCommandService, never()).restoreCouponByOrder(any())
    }

    @Test
    fun 주문_조회가_실패하면_결제_직접_취소를_503으로_거절하고_결제를_그대로_둔다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, BigDecimal("30000"), status = "PAYING")
        orderSummaryQueryPort.unavailable = true
        val payment = successPayment()
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(payment)
        Mockito.lenient().`when`(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        assertThatThrownBy { service().cancelPayment(PAYMENT_ID, BUYER_ID) }
            .isInstanceOf(PaymentOrderUnavailableException::class.java)
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        verify(paymentEventService, never()).publishPaymentCancelledEvent(any())
    }

    @Test
    fun 주문_취소에서_오는_내부_결제_취소는_주문_상태를_묻지_않는다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, BigDecimal("30000"), status = "CANCEL_REQUESTED")
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(successPayment())
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        val result = service().cancelPaymentInternal(PAYMENT_ID)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
        assertThat(orderSummaryQueryPort.requestedOrderIds).isEmpty()
    }

    companion object {
        private const val ORDER_ID = 547L
        private const val PAYMENT_ID = 5470L
        private const val BUYER_ID = 10L
    }
}
