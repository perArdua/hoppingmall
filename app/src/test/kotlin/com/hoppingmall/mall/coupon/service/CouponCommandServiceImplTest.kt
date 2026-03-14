package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.domain.Coupon
import com.hoppingmall.mall.coupon.domain.UserCoupon
import com.hoppingmall.mall.coupon.domain.repository.CouponRepository
import com.hoppingmall.mall.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.mall.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.mall.coupon.enum.CouponStatus
import com.hoppingmall.mall.coupon.enum.DiscountType
import com.hoppingmall.mall.coupon.enum.UserCouponStatus
import com.hoppingmall.mall.coupon.exception.*
import com.hoppingmall.mall.global.common.lock.DistributedLockExecutor
import com.hoppingmall.mall.support.fixture.*
import com.hoppingmall.mall.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@DisplayName("CouponCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponCommandServiceImplTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @Mock
    private lateinit var distributedLockExecutor: DistributedLockExecutor

    private lateinit var couponCommandService: CouponCommandServiceImpl

    @BeforeEach
    fun setUp() {
        couponCommandService = CouponCommandServiceImpl(couponRepository, userCouponRepository, distributedLockExecutor)
    }

    private fun stubLockExecutor() {
        whenever(distributedLockExecutor.withLock<Any>(any(), any(), any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<() -> Any>(3)
            action()
        }
    }

    @Nested
    @DisplayName("createCoupon")
    inner class CreateCoupon {

        @Test
        fun 쿠폰_생성_성공() {
            // Data
            val request = CouponCreateRequest(
                name = "신규 가입 쿠폰",
                code = "NEW2026",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal("5000"),
                minOrderAmount = BigDecimal("10000"),
                maxDiscountAmount = null,
                totalQuantity = 100,
                validFrom = LocalDateTime.now(),
                validTo = LocalDateTime.now().plusDays(30)
            )

            // Context
            whenever(couponRepository.save(any<Coupon>())).thenAnswer { invocation ->
                invocation.getArgument<Coupon>(0).withId(1L)
            }

            // Interaction
            val result = couponCommandService.createCoupon(request)

            // Assertions
            assertThat(result.name).isEqualTo("신규 가입 쿠폰")
            assertThat(result.code).isEqualTo("NEW2026")
            assertThat(result.discountType).isEqualTo(DiscountType.FIXED_AMOUNT)
            assertThat(result.totalQuantity).isEqualTo(100)
            verify(couponRepository).save(any())
        }
    }

    @Nested
    @DisplayName("changeCouponStatus")
    inner class ChangeCouponStatus {

        @Test
        fun 쿠폰_상태_변경_성공() {
            // Data
            val coupon = Coupon.fixture()

            // Context
            whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))
            whenever(couponRepository.save(any<Coupon>())).thenReturn(coupon)

            // Interaction
            val result = couponCommandService.changeCouponStatus(1L, CouponStatus.INACTIVE)

            // Assertions
            assertThat(result.status).isEqualTo(CouponStatus.INACTIVE)
            verify(couponRepository).save(any())
        }

        @Test
        fun 존재하지_않는_쿠폰_상태_변경_시_예외_발생() {
            // Context
            whenever(couponRepository.findById(999L)).thenReturn(Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.changeCouponStatus(999L, CouponStatus.INACTIVE) }
                .isInstanceOf(CouponNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("issueCoupon")
    inner class IssueCoupon {

        @BeforeEach
        fun setUpLock() {
            stubLockExecutor()
        }

        @Test
        fun 쿠폰_발급_성공() {
            // Data
            val userId = 1L
            val couponId = 1L
            val coupon = Coupon.fixture()

            // Context
            whenever(couponRepository.findActiveById(couponId)).thenReturn(coupon)
            whenever(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false)
            whenever(couponRepository.save(any<Coupon>())).thenReturn(coupon)
            whenever(userCouponRepository.save(any<UserCoupon>())).thenAnswer { invocation ->
                invocation.getArgument<UserCoupon>(0).withId(1L)
            }

            // Interaction
            val result = couponCommandService.issueCoupon(userId, couponId)

            // Assertions
            assertThat(result.couponId).isEqualTo(couponId)
            assertThat(result.status).isEqualTo(UserCouponStatus.ISSUED)
            verify(couponRepository).save(any())
            verify(userCouponRepository).save(any())
            verify(distributedLockExecutor).withLock<Any>(eq("coupon:issue:$couponId"), any(), any(), any())
        }

        @Test
        fun 존재하지_않는_쿠폰_발급_시_예외_발생() {
            // Context
            whenever(couponRepository.findActiveById(999L)).thenReturn(null)

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.issueCoupon(1L, 999L) }
                .isInstanceOf(CouponNotFoundException::class.java)
        }

        @Test
        fun 만료된_쿠폰_발급_시_예외_발생() {
            // Data
            val coupon = Coupon.expiredFixture()

            // Context
            whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.issueCoupon(1L, 1L) }
                .isInstanceOf(CouponExpiredException::class.java)
        }

        @Test
        fun 소진된_쿠폰_발급_시_예외_발생() {
            // Data
            val coupon = Coupon.exhaustedFixture()

            // Context
            whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.issueCoupon(1L, 1L) }
                .isInstanceOf(CouponExhaustedException::class.java)
        }

        @Test
        fun 이미_발급받은_쿠폰_재발급_시_예외_발생() {
            // Data
            val coupon = Coupon.fixture()

            // Context
            whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
            whenever(userCouponRepository.existsByUserIdAndCouponId(1L, 1L)).thenReturn(true)

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.issueCoupon(1L, 1L) }
                .isInstanceOf(CouponAlreadyIssuedException::class.java)
        }
    }

    @Nested
    @DisplayName("useCoupon")
    inner class UseCoupon {

        @Test
        fun 쿠폰_사용_성공() {
            // Data
            val userId = 1L
            val couponId = 1L
            val orderId = 10L
            val orderAmount = BigDecimal("50000")
            val userCoupon = UserCoupon.fixture(userId = userId, couponId = couponId)
            val coupon = Coupon.fixture(discountValue = BigDecimal("5000"))

            // Context
            whenever(userCouponRepository.findByUserIdAndCouponId(userId, couponId)).thenReturn(userCoupon)
            whenever(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon))
            whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

            // Interaction
            val discount = couponCommandService.useCoupon(userId, couponId, orderAmount, orderId)

            // Assertions
            assertThat(discount).isEqualByComparingTo(BigDecimal("5000"))
            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED)
            assertThat(userCoupon.orderId).isEqualTo(orderId)
            verify(userCouponRepository).save(any())
        }

        @Test
        fun 보유하지_않은_쿠폰_사용_시_예외_발생() {
            // Context
            whenever(userCouponRepository.findByUserIdAndCouponId(1L, 999L)).thenReturn(null)

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.useCoupon(1L, 999L, BigDecimal("50000"), 1L) }
                .isInstanceOf(CouponNotFoundException::class.java)
        }

        @Test
        fun 이미_사용한_쿠폰_사용_시_예외_발생() {
            // Data
            val userCoupon = UserCoupon.usedFixture()

            // Context
            whenever(userCouponRepository.findByUserIdAndCouponId(1L, 1L)).thenReturn(userCoupon)

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.useCoupon(1L, 1L, BigDecimal("50000"), 1L) }
                .isInstanceOf(CouponNotAvailableException::class.java)
        }

        @Test
        fun 만료된_쿠폰_사용_시_예외_발생() {
            // Data
            val userCoupon = UserCoupon.fixture()
            val coupon = Coupon.expiredFixture()

            // Context
            whenever(userCouponRepository.findByUserIdAndCouponId(1L, 1L)).thenReturn(userCoupon)
            whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.useCoupon(1L, 1L, BigDecimal("50000"), 1L) }
                .isInstanceOf(CouponExpiredException::class.java)
        }

        @Test
        fun 최소_주문_금액_미달_시_예외_발생() {
            // Data
            val userCoupon = UserCoupon.fixture()
            val coupon = Coupon.fixture(minOrderAmount = BigDecimal("50000"))

            // Context
            whenever(userCouponRepository.findByUserIdAndCouponId(1L, 1L)).thenReturn(userCoupon)
            whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))

            // Interaction & Assertions
            assertThatThrownBy { couponCommandService.useCoupon(1L, 1L, BigDecimal("10000"), 1L) }
                .isInstanceOf(CouponMinAmountNotMetException::class.java)
        }
    }

    @Nested
    @DisplayName("restoreCouponByPayment")
    inner class RestoreCouponByPayment {

        @Test
        fun 결제_실패_시_쿠폰_복구_성공() {
            // Data
            val userCoupon = UserCoupon.usedFixture()

            // Context
            whenever(userCouponRepository.findByUserIdAndCouponId(1L, 1L)).thenReturn(userCoupon)
            whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

            // Interaction
            couponCommandService.restoreCouponByPayment(1L, 1L)

            // Assertions
            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
            assertThat(userCoupon.usedAt).isNull()
            assertThat(userCoupon.orderId).isNull()
            verify(userCouponRepository).save(any())
        }

        @Test
        fun 쿠폰이_존재하지_않으면_무시() {
            // Context
            whenever(userCouponRepository.findByUserIdAndCouponId(1L, 999L)).thenReturn(null)

            // Interaction
            couponCommandService.restoreCouponByPayment(999L, 1L)

            // Assertions
            verify(userCouponRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("restoreCouponByOrder")
    inner class RestoreCouponByOrder {

        @Test
        fun 전액_환불_시_쿠폰_복구_성공() {
            // Data
            val userCoupon = UserCoupon.usedFixture(orderId = 1L)

            // Context
            whenever(userCouponRepository.findByOrderId(1L)).thenReturn(userCoupon)
            whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

            // Interaction
            couponCommandService.restoreCouponByOrder(1L)

            // Assertions
            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
            verify(userCouponRepository).save(any())
        }

        @Test
        fun 쿠폰이_사용되지_않은_주문이면_무시() {
            // Context
            whenever(userCouponRepository.findByOrderId(999L)).thenReturn(null)

            // Interaction
            couponCommandService.restoreCouponByOrder(999L)

            // Assertions
            verify(userCouponRepository, never()).save(any())
        }
    }
}
