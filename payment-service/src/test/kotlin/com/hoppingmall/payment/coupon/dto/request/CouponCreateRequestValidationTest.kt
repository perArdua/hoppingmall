package com.hoppingmall.payment.coupon.dto.request

import com.hoppingmall.payment.coupon.enum.DiscountType
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("쿠폰 만들기 요청의 비율 상한·기간 순서 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponCreateRequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private val validFrom = LocalDateTime.of(2026, 12, 1, 0, 0)

    @Test
    fun 비율_할인이_100을_넘으면_할인_값_오류다() {
        val violations = validator.validate(request(DiscountType.PERCENTAGE, "101"))

        assertThat(violations.map { it.propertyPath.toString() }).containsExactly("discountValue")
    }

    @Test
    fun 비율_할인_100은_통과한다() {
        assertThat(validator.validate(request(DiscountType.PERCENTAGE, "100"))).isEmpty()
    }

    @Test
    fun 정액_할인은_100을_넘어도_통과한다() {
        assertThat(validator.validate(request(DiscountType.FIXED_AMOUNT, "5000"))).isEmpty()
    }

    @Test
    fun 시작일이_종료일보다_늦으면_종료일_오류다() {
        val violations = validator.validate(request(validTo = validFrom.minusDays(1)))

        assertThat(violations.map { it.propertyPath.toString() }).containsExactly("validTo")
    }

    @Test
    fun 시작일과_종료일이_같으면_종료일_오류다() {
        val violations = validator.validate(request(validTo = validFrom))

        assertThat(violations.map { it.propertyPath.toString() }).containsExactly("validTo")
    }

    @Test
    fun 비율_초과와_거꾸로_된_기간이_함께면_오류가_둘이다() {
        val violations = validator.validate(request(DiscountType.PERCENTAGE, "150", validFrom.minusHours(1)))

        assertThat(violations.map { it.propertyPath.toString() }).containsExactlyInAnyOrder("discountValue", "validTo")
    }

    private fun request(
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: String = "1000",
        validTo: LocalDateTime = validFrom.plusDays(30)
    ) = CouponCreateRequest(
        name = "검증 쿠폰",
        code = "VALIDATE",
        discountType = discountType,
        discountValue = BigDecimal(discountValue),
        minOrderAmount = BigDecimal.ZERO,
        maxDiscountAmount = null,
        totalQuantity = 10,
        validFrom = validFrom,
        validTo = validTo
    )
}
