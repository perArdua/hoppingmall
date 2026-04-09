package com.hoppingmall.payment.coupon.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import com.hoppingmall.payment.coupon.exception.CouponNotFoundException
import com.hoppingmall.payment.internal.DistributedLockExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("CouponCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponCommandServiceImplTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @Mock
    private lateinit var distributedLockExecutor: DistributedLockExecutor

    @InjectMocks
    private lateinit var service: CouponCommandServiceImpl

    private fun createCoupon(
        id: Long = 1L,
        status: CouponStatus = CouponStatus.ACTIVE,
        totalQuantity: Int = 100,
        issuedQuantity: Int = 0,
        validFrom: LocalDateTime = LocalDateTime.now().minusDays(1),
        validTo: LocalDateTime = LocalDateTime.now().plusDays(30)
    ): Coupon {
        val coupon = Coupon.create(
            name = "테스트 쿠폰",
            code = "TEST-$id",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("1000"),
            minOrderAmount = BigDecimal("10000"),
            maxDiscountAmount = null,
            totalQuantity = totalQuantity,
            validFrom = validFrom,
            validTo = validTo
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(coupon, id)
        if (status != CouponStatus.ACTIVE) {
            coupon.changeStatus(status)
        }
        repeat(issuedQuantity) { coupon.issue() }
        return coupon
    }

    private fun createUserCoupon(id: Long = 1L, userId: Long = 10L, couponId: Long = 1L): UserCoupon {
        val userCoupon = UserCoupon.create(userId = userId, couponId = couponId)
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(userCoupon, id)
        return userCoupon
    }

    @Test
    fun 쿠폰_생성_시_저장된_쿠폰_응답을_반환한다() {
        val request = CouponCreateRequest(
            name = "신규 쿠폰",
            code = "NEW-001",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("2000"),
            minOrderAmount = BigDecimal("20000"),
            maxDiscountAmount = null,
            totalQuantity = 50,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(7)
        )
        val savedCoupon = createCoupon(id = 1L)
        whenever(couponRepository.save(any<Coupon>())).thenReturn(savedCoupon)

        val result = service.createCoupon(request)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("테스트 쿠폰")
        verify(couponRepository).save(any<Coupon>())
    }

    @Test
    fun 쿠폰_사용_성공_시_할인_금액을_반환한다() {
        val coupon = createCoupon(id = 1L)
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)

        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)
        whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        val result = service.useCoupon(10L, 1L, BigDecimal("30000"), 100L)

        assertThat(result).isEqualByComparingTo("1000")
        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED)
        verify(userCouponRepository).save(userCoupon)
    }

    @Test
    fun 쿠폰_사용_시_유저쿠폰이_없으면_예외를_던진다() {
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(null)

        assertThatThrownBy { service.useCoupon(10L, 1L, BigDecimal("30000"), 100L) }
            .isInstanceOf(CouponNotFoundException::class.java)
    }

    @Test
    fun 결제_취소_시_사용된_쿠폰을_복원한다() {
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        userCoupon.use(100L)
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        service.restoreCouponByPayment(1L, 10L)

        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
        verify(userCouponRepository).save(userCoupon)
    }

    @Test
    fun 결제_취소_시_유저쿠폰이_없으면_아무것도_하지_않는다() {
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(null)

        service.restoreCouponByPayment(1L, 10L)

        verify(userCouponRepository).findByUserIdAndCouponId(10L, 1L)
    }
}
