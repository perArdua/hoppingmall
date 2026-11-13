package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandServiceImpl
import com.hoppingmall.payment.coupon.service.CouponCompensationPublisher
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("결제 취소의 쿠폰 복원")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCancelCouponRestoreTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var paymentEventService: PaymentEventService

    @Mock
    private lateinit var paymentNotificationService: PaymentNotificationService

    @Mock
    private lateinit var paymentMetrics: PaymentMetrics

    @Mock
    private lateinit var pointEarnRateStrategy: PointEarnRateStrategy

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @Mock
    private lateinit var couponStockRedisRepository: CouponStockRedisRepository

    @Mock
    private lateinit var compensationPublisher: CouponCompensationPublisher

    @Mock
    private lateinit var compensationMetrics: CouponCompensationMetrics

    private val orderSummaryQueryPort = FakeOrderSummaryQueryPort()

    private fun commandService() = PaymentCommandServiceImpl(
        paymentRepository,
        paymentService,
        paymentEventService,
        paymentNotificationService,
        CouponCommandServiceImpl(
            couponRepository,
            userCouponRepository,
            couponStockRedisRepository,
            compensationPublisher,
            compensationMetrics
        ),
        paymentMetrics,
        pointEarnRateStrategy,
        orderSummaryQueryPort,
        transactionManager
    )

    private fun couponPayment(): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = USER_ID,
            amount = BigDecimal("29000"),
            method = PaymentMethod.CREDIT_CARD,
            couponId = COUPON_ID,
            couponDiscountAmount = BigDecimal("1000")
        )
        payment.updateStatus(
            newStatus = PaymentStatus.SUCCESS,
            transactionId = "txn-coupon-1",
            completedAt = LocalDateTime.of(2026, 11, 13, 10, 0, 0)
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, PAYMENT_ID)
        return payment
    }

    private fun usedCoupon(): UserCoupon {
        val userCoupon = UserCoupon.create(userId = USER_ID, couponId = COUPON_ID)
        userCoupon.use(ORDER_ID)
        return userCoupon
    }

    @Test
    fun 주문_취소로_오는_내부_결제_취소는_그_주문에_쓴_쿠폰을_돌려준다() {
        val userCoupon = usedCoupon()
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(couponPayment())
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(userCouponRepository.findByOrderId(ORDER_ID)).thenReturn(userCoupon)

        commandService().cancelPaymentInternal(PAYMENT_ID)

        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
        assertThat(userCoupon.orderId).isNull()
        verify(userCouponRepository).save(userCoupon)
    }

    @Test
    fun 구매자의_결제_직접_취소는_그_주문에_쓴_쿠폰을_돌려준다() {
        orderSummaryQueryPort.put(ORDER_ID, USER_ID, BigDecimal("30000"), status = "PAYING")
        val userCoupon = usedCoupon()
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(couponPayment())
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(userCouponRepository.findByOrderId(ORDER_ID)).thenReturn(userCoupon)

        commandService().cancelPayment(PAYMENT_ID, USER_ID)

        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
        assertThat(userCoupon.orderId).isNull()
        verify(userCouponRepository).save(userCoupon)
    }

    @Test
    fun 같은_결제_취소가_두_번_처리되어도_쿠폰은_한_번만_복원된다() {
        val userCoupon = usedCoupon()
        val payment = couponPayment()
        whenever(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(userCouponRepository.findByOrderId(ORDER_ID)).thenReturn(userCoupon)
        val service = commandService()

        service.cancelPaymentInternal(PAYMENT_ID)
        assertThatThrownBy { service.cancelPaymentInternal(PAYMENT_ID) }
            .isInstanceOf(PaymentInvalidStateException::class.java)

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELLED)
        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
        verify(userCouponRepository, times(1)).save(userCoupon)
    }

    companion object {
        private const val PAYMENT_ID = 1L
        private const val ORDER_ID = 100L
        private const val USER_ID = 10L
        private const val COUPON_ID = 7L
    }
}
