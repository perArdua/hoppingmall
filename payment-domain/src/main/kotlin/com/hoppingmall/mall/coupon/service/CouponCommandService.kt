package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse
import com.hoppingmall.mall.coupon.enum.CouponStatus
import java.math.BigDecimal

interface CouponCommandService {
    fun createCoupon(request: CouponCreateRequest): CouponResponse
    fun changeCouponStatus(couponId: Long, status: CouponStatus): CouponResponse
    fun issueCoupon(userId: Long, couponId: Long): UserCouponResponse
    fun useCoupon(userId: Long, couponId: Long, orderAmount: BigDecimal, orderId: Long): BigDecimal
    fun restoreCouponByPayment(couponId: Long, userId: Long)
    fun restoreCouponByOrder(orderId: Long)
}
