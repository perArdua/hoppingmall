package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.point.service.PointCommandService
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
import org.mockito.kotlin.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

@DisplayName("포인트를 쓴 결제의 금액 약속(결제 확인·적립·멤버십·쿠폰 순서)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentPointPromiseTest {

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
    private lateinit var pointCommandService: PointCommandService

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
            .thenReturn(BigDecimal("2000"))
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
        transactionManager,
        pointCommandService
    )

    @Test
    fun 결제_확인은_포인트를_빼기_전_주문_합계로_하고_쿠폰은_주문_합계로_계산한다() {
        val response = service().processPayment(request(couponId = COUPON_ID), BUYER_ID)

        assertThat(response.status).isEqualTo(PaymentStatus.SUCCESS)
        verify(couponCommandService).useCoupon(eq(BUYER_ID), eq(COUPON_ID), eq(ORDER_TOTAL), eq(ORDER_ID))
        assertThat(response.couponDiscountAmount).isEqualByComparingTo("2000")
        assertThat(response.pointAmount).isEqualByComparingTo("3000")
        assertThat(response.amount).isEqualByComparingTo("25000")
        verify(pointCommandService).useForPayment(eq(BUYER_ID), eq(response.id), eq(ORDER_ID), eq(BigDecimal("3000")))
    }

    @Test
    fun 적립과_멤버십_누적은_포인트와_쿠폰을_뺀_현금분으로_요청한다() {
        service().processPayment(request(couponId = COUPON_ID), BUYER_ID)

        val earned = argumentCaptor<Payment>()
        verify(paymentEventService).publishPointEarnRequestEvent(earned.capture(), eq(BigDecimal("0.01")))
        assertThat(earned.firstValue.amount).isEqualByComparingTo("25000")
        val membership = argumentCaptor<Payment>()
        verify(paymentEventService).publishMembershipUpdateEvent(membership.capture())
        assertThat(membership.firstValue.amount).isEqualByComparingTo("25000")
        assertThat(membership.firstValue.pointAmount).isEqualByComparingTo("3000")
    }

    @Test
    fun 결제_취소는_쓴_포인트를_되돌리고_멤버십은_현금분으로_되돌린다() {
        val paid = service().processPayment(request(couponId = null), BUYER_ID)
        val payment = Payment.create(
            orderId = ORDER_ID, userId = BUYER_ID, amount = BigDecimal("27000"),
            method = PaymentMethod.CREDIT_CARD, pointAmount = BigDecimal("3000")
        )
        BaseEntity::class.java.getDeclaredField("id").apply { isAccessible = true }.set(payment, paid.id)
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "tx-point", completedAt = null)
        Mockito.`when`(paymentRepository.findByIdForUpdate(paid.id)).thenReturn(payment)

        service().cancelPaymentInternal(paid.id)

        verify(pointCommandService).restoreUsedPoints(
            eq(BUYER_ID), eq(paid.id), eq(ORDER_ID), eq("use-cancel-${paid.id}"), any()
        )
        val reverted = argumentCaptor<Payment>()
        verify(paymentEventService).publishMembershipCancelRevertEvent(reverted.capture())
        assertThat(reverted.firstValue.membershipCancelRevertAmount()).isEqualByComparingTo("27000")
    }

    private fun request(couponId: Long?) = PaymentRequest(
        orderId = ORDER_ID,
        amount = ORDER_TOTAL,
        method = PaymentMethod.CREDIT_CARD,
        pointAmount = BigDecimal("3000"),
        couponId = couponId
    )

    companion object {
        private const val PAYMENT_ID = 500L
        private const val ORDER_ID = 1242L
        private const val BUYER_ID = 12L
        private const val COUPON_ID = 4L
        private val ORDER_TOTAL = BigDecimal("30000.00")
    }
}
