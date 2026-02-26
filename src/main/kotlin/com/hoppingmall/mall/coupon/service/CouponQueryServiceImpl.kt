package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.domain.repository.CouponRepository
import com.hoppingmall.mall.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse
import com.hoppingmall.mall.coupon.exception.CouponNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CouponQueryServiceImpl(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository
) : CouponQueryService {

    override fun getAvailableCoupons(): List<CouponResponse> {
        return couponRepository.findAvailableCoupons()
            .map { CouponResponse.from(it) }
    }

    override fun getAllCoupons(): List<CouponResponse> {
        return couponRepository.findAllActive()
            .map { CouponResponse.from(it) }
    }

    override fun getMyCoupons(userId: Long): List<UserCouponResponse> {
        val userCoupons = userCouponRepository.findByUserId(userId)
        return userCoupons.map { userCoupon ->
            val coupon = couponRepository.findById(userCoupon.couponId)
                .orElseThrow { CouponNotFoundException() }
            UserCouponResponse.from(userCoupon, coupon)
        }
    }
}
