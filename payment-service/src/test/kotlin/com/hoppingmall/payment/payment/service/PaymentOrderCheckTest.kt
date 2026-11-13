package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.PaymentResult
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentAccessDeniedException
import com.hoppingmall.payment.payment.exception.PaymentDuplicateException
import com.hoppingmall.payment.payment.exception.PaymentException
import com.hoppingmall.payment.payment.exception.PaymentInvalidAmountException
import com.hoppingmall.payment.payment.exception.PaymentOrderNotFoundException
import com.hoppingmall.payment.payment.exception.PaymentOrderNotPayableException
import com.hoppingmall.payment.payment.exception.PaymentOrderUnavailableException
import com.hoppingmall.payment.payment.exception.PaymentPointUseNotAllowedException
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal

@DisplayName("결제 요청의 주문 확인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentOrderCheckTest {

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
    private lateinit var pointEarnRateStrategy: PointEarnRateStrategy

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private val orderSummaryQueryPort = FakeOrderSummaryQueryPort()

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionManager.getTransaction(anyOrNull())).thenReturn(SimpleTransactionStatus())
        Mockito.lenient().`when`(paymentRepository.save(any<Payment>())).thenAnswer { invocation ->
            val payment = invocation.arguments[0] as Payment
            if (payment.id == null) {
                val idField = BaseEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(payment, PAYMENT_ID)
            }
            payment
        }
        Mockito.lenient().`when`(paymentService.processPayment(any())).thenAnswer { invocation ->
            PaymentResult.success(invocation.arguments[0] as Payment, "tx-order-check")
        }
        Mockito.lenient().`when`(pointEarnRateStrategy.getEarnRate(any())).thenReturn(BigDecimal("0.01"))
    }

    private fun service() = PaymentCommandServiceImpl(
        paymentRepository,
        paymentService,
        paymentEventService,
        paymentNotificationService,
        couponCommandService,
        PaymentMetrics(SimpleMeterRegistry()),
        pointEarnRateStrategy,
        orderSummaryQueryPort,
        transactionManager
    )

    private fun request(
        amount: BigDecimal = ORDER_TOTAL,
        pointAmount: BigDecimal = BigDecimal.ZERO,
        couponId: Long? = null
    ) = PaymentRequest(
        orderId = ORDER_ID,
        amount = amount,
        method = PaymentMethod.CREDIT_CARD,
        pointAmount = pointAmount,
        couponId = couponId
    )

    private fun assertRejected(status: HttpStatus, type: Class<out PaymentException>, action: () -> Unit) {
        assertThatThrownBy { action() }
            .isInstanceOf(type)
            .satisfies({ assertThat((it as PaymentException).errorCode.status).isEqualTo(status) })
        verify(paymentRepository, never()).save(any<Payment>())
        verify(paymentService, never()).processPayment(any())
        verifyNoInteractions(couponCommandService)
    }

    @Test
    fun 주문_총액과_다른_금액의_결제는_400으로_거절하고_결제를_만들지_않는다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)

        assertRejected(HttpStatus.BAD_REQUEST, PaymentInvalidAmountException::class.java) {
            service().processPayment(request(amount = BigDecimal("1")), BUYER_ID)
        }
    }

    @Test
    fun 다른_구매자의_주문은_403으로_거절하고_결제를_만들지_않는다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)

        assertRejected(HttpStatus.FORBIDDEN, PaymentAccessDeniedException::class.java) {
            service().processPayment(request(), OTHER_USER_ID)
        }
    }

    @Test
    fun 결제_대기가_아닌_주문은_400으로_거절하고_결제를_만들지_않는다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL, status = "PAID")

        assertRejected(HttpStatus.BAD_REQUEST, PaymentOrderNotPayableException::class.java) {
            service().processPayment(request(), BUYER_ID)
        }
    }

    @Test
    fun 없는_주문은_404로_거절하고_결제를_만들지_않는다() {
        assertRejected(HttpStatus.NOT_FOUND, PaymentOrderNotFoundException::class.java) {
            service().processPayment(request(), BUYER_ID)
        }
    }

    @Test
    fun 주문_조회가_실패하면_503으로_거절하고_결제를_받지_않는다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)
        orderSummaryQueryPort.unavailable = true

        assertRejected(HttpStatus.SERVICE_UNAVAILABLE, PaymentOrderUnavailableException::class.java) {
            service().processPayment(request(), BUYER_ID)
        }
    }

    @Test
    fun 주문과_맞는_결제는_쿠폰_할인_전_총액으로_확인하고_할인은_그_뒤에_뺀다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)
        whenever(couponCommandService.useCoupon(BUYER_ID, COUPON_ID, ORDER_TOTAL, ORDER_ID)).thenReturn(BigDecimal("3000"))

        val result = service().processPayment(request(couponId = COUPON_ID), BUYER_ID)

        assertThat(orderSummaryQueryPort.requestedOrderIds).containsExactly(ORDER_ID)
        assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("27000"))
        assertThat(result.couponDiscountAmount).isEqualByComparingTo(BigDecimal("3000"))
        val saved = argumentCaptor<Payment>()
        verify(paymentRepository, atLeastOnce()).save(saved.capture())
        assertThat(saved.firstValue.orderId).isEqualTo(ORDER_ID)
        assertThat(saved.firstValue.userId).isEqualTo(BUYER_ID)
    }

    @Test
    fun 포인트_사용액이_있는_결제는_400으로_거절하고_결제를_만들지_않는다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)

        assertRejected(HttpStatus.BAD_REQUEST, PaymentPointUseNotAllowedException::class.java) {
            service().processPayment(request(pointAmount = BigDecimal("1000")), BUYER_ID)
        }
    }

    @Test
    fun 같은_주문에_결제가_이미_있으면_409로_거절하고_외부_결제를_부르지_않는다() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)
        doThrow(DataIntegrityViolationException("Duplicate entry for key 'uk_payments_order_id'"))
            .whenever(paymentRepository).save(any<Payment>())

        assertThatThrownBy { service().processPayment(request(), BUYER_ID) }
            .isInstanceOf(PaymentDuplicateException::class.java)
            .satisfies({ assertThat((it as PaymentException).errorCode.status).isEqualTo(HttpStatus.CONFLICT) })
        verify(paymentService, never()).processPayment(any())
        verify(paymentEventService, never()).publishPaymentCompletedEvent(any())
    }

    companion object {
        private const val ORDER_ID = 700L
        private const val PAYMENT_ID = 7001L
        private const val BUYER_ID = 10L
        private const val OTHER_USER_ID = 99L
        private const val COUPON_ID = 5L
        private val ORDER_TOTAL = BigDecimal("30000.00")
    }
}
