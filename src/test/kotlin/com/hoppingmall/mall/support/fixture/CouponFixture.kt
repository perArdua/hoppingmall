package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.coupon.domain.Coupon
import com.hoppingmall.mall.coupon.enum.CouponStatus
import com.hoppingmall.mall.coupon.enum.DiscountType
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal
import java.time.LocalDateTime

fun Coupon.Companion.fixture(
    name: String = "테스트 쿠폰",
    code: String = "TEST001",
    discountType: DiscountType = DiscountType.FIXED_AMOUNT,
    discountValue: BigDecimal = BigDecimal("5000"),
    minOrderAmount: BigDecimal = BigDecimal("10000"),
    maxDiscountAmount: BigDecimal? = null,
    totalQuantity: Int = 100,
    validFrom: LocalDateTime = LocalDateTime.now().minusDays(1),
    validTo: LocalDateTime = LocalDateTime.now().plusDays(30)
): Coupon {
    return Coupon.create(
        name = name,
        code = code,
        discountType = discountType,
        discountValue = discountValue,
        minOrderAmount = minOrderAmount,
        maxDiscountAmount = maxDiscountAmount,
        totalQuantity = totalQuantity,
        validFrom = validFrom,
        validTo = validTo
    ).withId(1L)
}

fun Coupon.Companion.percentageFixture(
    name: String = "10% 할인 쿠폰",
    code: String = "PCT010",
    discountValue: BigDecimal = BigDecimal("10"),
    minOrderAmount: BigDecimal = BigDecimal("10000"),
    maxDiscountAmount: BigDecimal = BigDecimal("50000"),
    totalQuantity: Int = 100,
    validFrom: LocalDateTime = LocalDateTime.now().minusDays(1),
    validTo: LocalDateTime = LocalDateTime.now().plusDays(30)
): Coupon {
    return Coupon.create(
        name = name,
        code = code,
        discountType = DiscountType.PERCENTAGE,
        discountValue = discountValue,
        minOrderAmount = minOrderAmount,
        maxDiscountAmount = maxDiscountAmount,
        totalQuantity = totalQuantity,
        validFrom = validFrom,
        validTo = validTo
    ).withId(1L)
}

fun Coupon.Companion.expiredFixture(
    name: String = "만료 쿠폰",
    code: String = "EXP001"
): Coupon {
    return Coupon.create(
        name = name,
        code = code,
        discountType = DiscountType.FIXED_AMOUNT,
        discountValue = BigDecimal("5000"),
        minOrderAmount = BigDecimal("10000"),
        maxDiscountAmount = null,
        totalQuantity = 100,
        validFrom = LocalDateTime.now().minusDays(30),
        validTo = LocalDateTime.now().minusDays(1)
    ).withId(1L)
}

fun Coupon.Companion.exhaustedFixture(
    name: String = "소진 쿠폰",
    code: String = "EXHST01",
    totalQuantity: Int = 1
): Coupon {
    return Coupon.create(
        name = name,
        code = code,
        discountType = DiscountType.FIXED_AMOUNT,
        discountValue = BigDecimal("5000"),
        minOrderAmount = BigDecimal("10000"),
        maxDiscountAmount = null,
        totalQuantity = totalQuantity,
        validFrom = LocalDateTime.now().minusDays(1),
        validTo = LocalDateTime.now().plusDays(30)
    ).apply {
        repeat(totalQuantity) { issue() }
    }.withId(1L)
}
