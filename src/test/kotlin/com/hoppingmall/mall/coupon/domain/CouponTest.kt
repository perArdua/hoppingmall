package com.hoppingmall.mall.coupon.domain

import com.hoppingmall.mall.coupon.enum.CouponStatus
import com.hoppingmall.mall.coupon.enum.DiscountType
import com.hoppingmall.mall.support.fixture.exhaustedFixture
import com.hoppingmall.mall.support.fixture.expiredFixture
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.percentageFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Coupon")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CouponTest {

    @Nested
    @DisplayName("calculateDiscount")
    inner class CalculateDiscount {

        @Test
        fun 정액_할인_계산() {
            val coupon = Coupon.fixture(discountValue = BigDecimal("5000"))

            val discount = coupon.calculateDiscount(BigDecimal("50000"))

            assertThat(discount).isEqualByComparingTo(BigDecimal("5000"))
        }

        @Test
        fun 정률_할인_계산() {
            val coupon = Coupon.percentageFixture(
                discountValue = BigDecimal("10"),
                maxDiscountAmount = BigDecimal("50000")
            )

            val discount = coupon.calculateDiscount(BigDecimal("100000"))

            assertThat(discount).isEqualByComparingTo(BigDecimal("10000"))
        }

        @Test
        fun 정률_할인_최대_할인_금액_적용() {
            val coupon = Coupon.percentageFixture(
                discountValue = BigDecimal("50"),
                maxDiscountAmount = BigDecimal("10000")
            )

            val discount = coupon.calculateDiscount(BigDecimal("100000"))

            assertThat(discount).isEqualByComparingTo(BigDecimal("10000"))
        }

        @Test
        fun 할인_금액이_주문_금액을_초과하면_주문_금액_반환() {
            val coupon = Coupon.fixture(
                discountValue = BigDecimal("50000"),
                minOrderAmount = BigDecimal("1000")
            )

            val discount = coupon.calculateDiscount(BigDecimal("3000"))

            assertThat(discount).isEqualByComparingTo(BigDecimal("3000"))
        }
    }

    @Nested
    @DisplayName("isValid")
    inner class IsValid {

        @Test
        fun 유효한_쿠폰() {
            val coupon = Coupon.fixture()

            assertThat(coupon.isValid()).isTrue()
        }

        @Test
        fun 만료된_쿠폰은_유효하지_않음() {
            val coupon = Coupon.expiredFixture()

            assertThat(coupon.isValid()).isFalse()
        }

        @Test
        fun 비활성화된_쿠폰은_유효하지_않음() {
            val coupon = Coupon.fixture().apply { changeStatus(CouponStatus.INACTIVE) }

            assertThat(coupon.isValid()).isFalse()
        }
    }

    @Nested
    @DisplayName("issue")
    inner class Issue {

        @Test
        fun 쿠폰_발급_시_발급_수량_증가() {
            val coupon = Coupon.fixture(totalQuantity = 100)
            val beforeQuantity = coupon.issuedQuantity

            coupon.issue()

            assertThat(coupon.issuedQuantity).isEqualTo(beforeQuantity + 1)
        }
    }

    @Nested
    @DisplayName("isExhausted")
    inner class IsExhausted {

        @Test
        fun 수량_소진된_쿠폰() {
            val coupon = Coupon.exhaustedFixture()

            assertThat(coupon.isExhausted()).isTrue()
        }

        @Test
        fun 수량_남은_쿠폰() {
            val coupon = Coupon.fixture(totalQuantity = 100)

            assertThat(coupon.isExhausted()).isFalse()
        }
    }

    @Nested
    @DisplayName("changeStatus")
    inner class ChangeStatus {

        @Test
        fun 쿠폰_상태_변경() {
            val coupon = Coupon.fixture()

            coupon.changeStatus(CouponStatus.INACTIVE)

            assertThat(coupon.status).isEqualTo(CouponStatus.INACTIVE)
        }
    }
}
