package com.hoppingmall.payment.coupon.dto.response

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import java.math.BigDecimal
import java.time.LocalDateTime

data class CouponResponse(
    val id: Long,
    val name: String,
    val code: String,
    val discountType: DiscountType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal,
    val maxDiscountAmount: BigDecimal?,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
    val status: CouponStatus,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(coupon: Coupon): CouponResponse {
            return CouponResponse(
                id = coupon.id!!,
                name = coupon.name,
                code = coupon.code,
                discountType = coupon.discountType,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                maxDiscountAmount = coupon.maxDiscountAmount,
                totalQuantity = coupon.totalQuantity,
                issuedQuantity = coupon.issuedQuantity,
                validFrom = coupon.validFrom,
                validTo = coupon.validTo,
                status = coupon.status,
                createdAt = coupon.createdAt
            )
        }
    }
}
