package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.coupon.domain.UserCoupon
import com.hoppingmall.mall.coupon.enum.UserCouponStatus
import com.hoppingmall.mall.support.withId

fun UserCoupon.Companion.fixture(
    userId: Long = 1L,
    couponId: Long = 1L
): UserCoupon {
    return UserCoupon.create(
        userId = userId,
        couponId = couponId
    ).withId(1L)
}

fun UserCoupon.Companion.usedFixture(
    userId: Long = 1L,
    couponId: Long = 1L,
    orderId: Long = 1L
): UserCoupon {
    return UserCoupon.create(
        userId = userId,
        couponId = couponId
    ).apply {
        use(orderId)
    }.withId(1L)
}
