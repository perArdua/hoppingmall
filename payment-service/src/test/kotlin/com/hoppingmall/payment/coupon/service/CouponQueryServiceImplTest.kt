package com.hoppingmall.payment.coupon.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.enum.DiscountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("CouponQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponQueryServiceImplTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @InjectMocks
    private lateinit var couponQueryService: CouponQueryServiceImpl

    private fun setEntityFields(entity: Any, id: Long) {
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(entity, id)
    }

    private fun createCoupon(id: Long = 1L): Coupon {
        val coupon = Coupon.create(
            name = "테스트 쿠폰",
            code = "TEST001",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("1000"),
            minOrderAmount = BigDecimal("5000"),
            maxDiscountAmount = null,
            totalQuantity = 100,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(30)
        )
        setEntityFields(coupon, id)
        return coupon
    }

    @Test
    fun 사용_가능한_쿠폰_목록을_조회한다() {
        val coupon = createCoupon()
        whenever(couponRepository.findAvailableCoupons(any(), any())).thenReturn(listOf(coupon))

        val result = couponQueryService.getAvailableCoupons()

        assertThat(result).hasSize(1)
    }

    @Test
    fun 전체_쿠폰_목록을_조회한다() {
        val coupon = createCoupon()
        whenever(couponRepository.findAllActive()).thenReturn(listOf(coupon))

        val result = couponQueryService.getAllCoupons()

        assertThat(result).hasSize(1)
    }

    @Test
    fun 내_쿠폰을_페이지네이션으로_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val coupon = createCoupon(1L)
        val userCoupon = UserCoupon.create(userId = 1L, couponId = 1L)
        setEntityFields(userCoupon, 1L)

        val slice = SliceImpl(listOf(userCoupon), pageable, false)
        whenever(userCouponRepository.findByUserId(1L, pageable)).thenReturn(slice)
        whenever(couponRepository.findAllById(listOf(1L))).thenReturn(listOf(coupon))

        val result = couponQueryService.getMyCoupons(1L, pageable)

        assertThat(result.content).hasSize(1)
    }
}
