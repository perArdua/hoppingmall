package com.hoppingmall.payment.coupon.domain

import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("Coupon")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponTest {

    private fun createCoupon(
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: BigDecimal = BigDecimal("1000"),
        minOrderAmount: BigDecimal = BigDecimal("10000"),
        maxDiscountAmount: BigDecimal? = null,
        totalQuantity: Int = 100,
        issuedQuantity: Int = 0,
        validFrom: LocalDateTime = LocalDateTime.now().minusDays(1),
        validTo: LocalDateTime = LocalDateTime.now().plusDays(30),
        status: CouponStatus = CouponStatus.ACTIVE
    ): Coupon {
        val coupon = Coupon.create(
            name = "테스트 쿠폰",
            code = "TEST-001",
            discountType = discountType,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            maxDiscountAmount = maxDiscountAmount,
            totalQuantity = totalQuantity,
            validFrom = validFrom,
            validTo = validTo
        )
        repeat(issuedQuantity) { coupon.issue() }
        if (status != CouponStatus.ACTIVE) {
            coupon.changeStatus(status)
        }
        return coupon
    }

    @Test
    fun create_팩토리_메서드로_쿠폰을_생성한다() {
        val coupon = Coupon.create(
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

        assertThat(coupon.name).isEqualTo("신규 쿠폰")
        assertThat(coupon.code).isEqualTo("NEW-001")
        assertThat(coupon.status).isEqualTo(CouponStatus.ACTIVE)
        assertThat(coupon.issuedQuantity).isEqualTo(0)
    }

    @Test
    fun calculateDiscount_FIXED_AMOUNT_고정_할인_금액을_반환한다() {
        val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = BigDecimal("1000"))

        val discount = coupon.calculateDiscount(BigDecimal("30000"))

        assertThat(discount).isEqualByComparingTo("1000")
    }

    @Test
    fun calculateDiscount_FIXED_AMOUNT_할인금액이_주문금액보다_크면_주문금액을_반환한다() {
        val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = BigDecimal("50000"))

        val discount = coupon.calculateDiscount(BigDecimal("10000"))

        assertThat(discount).isEqualByComparingTo("10000")
    }

    @Test
    fun calculateDiscount_PERCENTAGE_비율_할인_금액을_반환한다() {
        val coupon = createCoupon(discountType = DiscountType.PERCENTAGE, discountValue = BigDecimal("10"))

        val discount = coupon.calculateDiscount(BigDecimal("30000"))

        assertThat(discount).isEqualByComparingTo("3000")
    }

    @Test
    fun calculateDiscount_PERCENTAGE_최대_할인금액을_초과하지_않는다() {
        val coupon = createCoupon(
            discountType = DiscountType.PERCENTAGE,
            discountValue = BigDecimal("20"),
            maxDiscountAmount = BigDecimal("2000")
        )

        val discount = coupon.calculateDiscount(BigDecimal("30000"))

        assertThat(discount).isEqualByComparingTo("2000")
    }

    @Test
    fun calculateDiscount_PERCENTAGE_최대_할인금액_이하면_계산값을_반환한다() {
        val coupon = createCoupon(
            discountType = DiscountType.PERCENTAGE,
            discountValue = BigDecimal("5"),
            maxDiscountAmount = BigDecimal("5000")
        )

        val discount = coupon.calculateDiscount(BigDecimal("30000"))

        assertThat(discount).isEqualByComparingTo("1500")
    }

    @Test
    fun isValid_유효한_쿠폰은_true를_반환한다() {
        val coupon = createCoupon(
            status = CouponStatus.ACTIVE,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(30)
        )

        assertThat(coupon.isValid()).isTrue()
    }

    @Test
    fun isValid_INACTIVE_상태면_false를_반환한다() {
        val coupon = createCoupon(status = CouponStatus.INACTIVE)

        assertThat(coupon.isValid()).isFalse()
    }

    @Test
    fun isValid_유효기간_이전이면_false를_반환한다() {
        val coupon = createCoupon(
            validFrom = LocalDateTime.now().plusDays(1),
            validTo = LocalDateTime.now().plusDays(30)
        )

        assertThat(coupon.isValid()).isFalse()
    }

    @Test
    fun isExpired_유효기간이_지나면_true를_반환한다() {
        val coupon = createCoupon(validTo = LocalDateTime.now().minusDays(1))

        assertThat(coupon.isExpired()).isTrue()
    }

    @Test
    fun isExpired_유효기간_내이면_false를_반환한다() {
        val coupon = createCoupon(validTo = LocalDateTime.now().plusDays(30))

        assertThat(coupon.isExpired()).isFalse()
    }

    @Test
    fun isExhausted_발급수량이_전체수량과_같으면_true를_반환한다() {
        val coupon = createCoupon(totalQuantity = 10, issuedQuantity = 10)

        assertThat(coupon.isExhausted()).isTrue()
    }

    @Test
    fun isExhausted_발급수량이_전체수량보다_작으면_false를_반환한다() {
        val coupon = createCoupon(totalQuantity = 10, issuedQuantity = 9)

        assertThat(coupon.isExhausted()).isFalse()
    }

    @Test
    fun issue_발급수량을_증가시킨다() {
        val coupon = createCoupon(issuedQuantity = 0)

        coupon.issue()

        assertThat(coupon.issuedQuantity).isEqualTo(1)
    }

    @Test
    fun changeStatus_쿠폰_상태를_변경한다() {
        val coupon = createCoupon(status = CouponStatus.ACTIVE)

        coupon.changeStatus(CouponStatus.INACTIVE)

        assertThat(coupon.status).isEqualTo(CouponStatus.INACTIVE)
    }
}
