package com.hoppingmall.payment.coupon.domain.repository

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("활성 쿠폰 전체 조회 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponFindAllActiveMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var couponRepository: CouponRepository

    private fun saveCoupon(code: String, status: CouponStatus): Coupon {
        val coupon = Coupon.create(
            name = code,
            code = code,
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("1000"),
            minOrderAmount = BigDecimal.ZERO,
            maxDiscountAmount = null,
            totalQuantity = 10,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(1)
        )
        coupon.changeStatus(status)
        return couponRepository.save(coupon)
    }

    @Test
    fun 활성_쿠폰_전체_조회는_비활성_쿠폰을_돌려주지_않는다() {
        val active = saveCoupon("ACTIVE-758", CouponStatus.ACTIVE)
        saveCoupon("INACTIVE-758", CouponStatus.INACTIVE)

        assertThat(couponRepository.findAllActive().map { it.id }).containsExactly(active.id)
    }
}
