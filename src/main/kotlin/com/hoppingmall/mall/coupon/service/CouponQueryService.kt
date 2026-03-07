package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface CouponQueryService {
    fun getAvailableCoupons(): List<CouponResponse>
    fun getAllCoupons(): List<CouponResponse>
    fun getMyCoupons(userId: Long, pageable: Pageable): Slice<UserCouponResponse>
}
