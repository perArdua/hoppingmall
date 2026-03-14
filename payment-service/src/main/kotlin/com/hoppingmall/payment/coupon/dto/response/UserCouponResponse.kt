package com.hoppingmall.payment.coupon.dto.response

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class UserCouponResponse(
    val id: Long,
    val couponId: Long,
    val couponName: String,
    val discountType: DiscountType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal,
    val maxDiscountAmount: BigDecimal?,
    val status: UserCouponStatus,
    val usedAt: LocalDateTime?,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(userCoupon: UserCoupon, coupon: Coupon): UserCouponResponse {
            return UserCouponResponse(
                id = userCoupon.id!!,
                couponId = coupon.id!!,
                couponName = coupon.name,
                discountType = coupon.discountType,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                maxDiscountAmount = coupon.maxDiscountAmount,
                status = userCoupon.status,
                usedAt = userCoupon.usedAt,
                validFrom = coupon.validFrom,
                validTo = coupon.validTo,
                createdAt = userCoupon.createdAt
            )
        }
    }
}
