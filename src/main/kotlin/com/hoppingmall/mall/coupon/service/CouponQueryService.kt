package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse

interface CouponQueryService {
    fun getAvailableCoupons(): List<CouponResponse>
    fun getAllCoupons(): List<CouponResponse>
    fun getMyCoupons(userId: Long): List<UserCouponResponse>
}
