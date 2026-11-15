package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

@DisplayName("쿠폰 할인으로 0원이 된 결제")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentZeroAmountTest {

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
    private val nextPaymentId = AtomicLong(PAYMENT_ID)

    @BeforeEach
    fun setUp() {
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)
        Mockito.lenient().`when`(transactionManager.getTransaction(anyOrNull())).thenReturn(SimpleTransactionStatus())
        Mockito.lenient().`when`(paymentRepository.save(any<Payment>())).thenAnswer { invocation ->
            val payment = invocation.arguments[0] as Payment
            if (payment.id == null) {
                val idField = BaseEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(payment, nextPaymentId.getAndIncrement())
            }
            payment
        }
        val pg = MockPaymentService()
        Mockito.lenient().`when`(paymentService.processPayment(any())).thenAnswer { invocation ->
            pg.processPayment(invocation.arguments[0] as Payment)
        }
        Mockito.lenient().`when`(pointEarnRateStrategy.getEarnRate(any())).thenReturn(BigDecimal("0.01"))
        Mockito.lenient().`when`(couponCommandService.useCoupon(eq(BUYER_ID), eq(COUPON_ID), any(), eq(ORDER_ID)))
            .thenReturn(ORDER_TOTAL)
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

    private fun request() = PaymentRequest(
        orderId = ORDER_ID,
        amount = ORDER_TOTAL,
        method = PaymentMethod.CREDIT_CARD,
        couponId = COUPON_ID
    )

    @Test
    fun 쿠폰_할인이_주문_합계와_같으면_PG를_부르지_않고_0원_결제를_완료한다() {
        val response = service().processPayment(request(), BUYER_ID)

        assertThat(response.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(response.amount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(response.couponDiscountAmount).isEqualByComparingTo(ORDER_TOTAL)
        assertThat(response.transactionId).isNotBlank()
        assertThat(response.completedAt).isNotNull()
        verify(paymentService, never()).processPayment(any())
        verify(couponCommandService, never()).restoreCouponByPayment(any(), any())
    }

    @Test
    fun 영원_결제는_결제_완료_이벤트와_알림을_내고_적립과_멤버십_요청은_내지_않는다() {
        service().processPayment(request(), BUYER_ID)

        val completed = argumentCaptor<Payment>()
        verify(paymentEventService).publishPaymentCompletedEvent(completed.capture())
        assertThat(completed.firstValue.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(completed.firstValue.transactionId).isNotBlank()
        verify(paymentNotificationService).publishPaymentCompletedNotification(any())
        verify(paymentEventService, never()).publishPointEarnRequestEvent(any(), any())
        verify(paymentEventService, never()).publishMembershipUpdateEvent(any())
        verify(pointEarnRateStrategy, never()).getEarnRate(any())
    }

    @Test
    fun 영원_결제의_거래_ID는_결제마다_다르다() {
        orderSummaryQueryPort.put(OTHER_ORDER_ID, BUYER_ID, ORDER_TOTAL)
        Mockito.lenient().`when`(couponCommandService.useCoupon(eq(BUYER_ID), eq(COUPON_ID), any(), eq(OTHER_ORDER_ID)))
            .thenReturn(ORDER_TOTAL)

        val first = service().processPayment(request(), BUYER_ID)
        val second = service().processPayment(request().copy(orderId = OTHER_ORDER_ID), BUYER_ID)

        assertThat(first.transactionId).isNotEqualTo(second.transactionId)
    }

    @Test
    fun 영원_결제도_결제_대기_주문이면_취소할_수_있고_취소_이벤트를_낸다() {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = BUYER_ID,
            amount = BigDecimal.ZERO,
            method = PaymentMethod.CREDIT_CARD,
            couponId = COUPON_ID,
            couponDiscountAmount = ORDER_TOTAL
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, PAYMENT_ID)
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "FREE_$PAYMENT_ID")
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(payment)

        val response = service().cancelPayment(PAYMENT_ID, BUYER_ID)

        assertThat(response.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(couponCommandService).restoreCouponByOrder(ORDER_ID)
        verify(paymentEventService).publishPaymentCancelledEvent(payment)
    }

    companion object {
        private const val PAYMENT_ID = 700L
        private const val ORDER_ID = 984L
        private const val OTHER_ORDER_ID = 985L
        private const val BUYER_ID = 10L
        private const val COUPON_ID = 3L
        private val ORDER_TOTAL = BigDecimal("30000.00")
    }
}
