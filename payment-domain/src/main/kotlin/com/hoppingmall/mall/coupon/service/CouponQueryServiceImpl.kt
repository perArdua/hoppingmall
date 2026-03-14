package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.domain.repository.CouponRepository
import com.hoppingmall.mall.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse
import com.hoppingmall.mall.coupon.exception.CouponNotFoundException
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CouponQueryServiceImpl(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository
) : CouponQueryService {

    @Cacheable(cacheNames = ["coupon:available"], key = "'all'", sync = true)
    override fun getAvailableCoupons(): List<CouponResponse> {
        return couponRepository.findAvailableCoupons()
            .map { CouponResponse.from(it) }
    }

    @Cacheable(cacheNames = ["coupon:all"], key = "'all'", sync = true)
    override fun getAllCoupons(): List<CouponResponse> {
        return couponRepository.findAllActive()
            .map { CouponResponse.from(it) }
    }

    override fun getMyCoupons(userId: Long, pageable: Pageable): Slice<UserCouponResponse> {
        val userCouponSlice = userCouponRepository.findByUserId(userId, pageable)
        val couponIds = userCouponSlice.content.map { it.couponId }
        val couponMap = couponRepository.findAllById(couponIds).associateBy { it.id }

        val responses = userCouponSlice.content.map { userCoupon ->
            val coupon = couponMap[userCoupon.couponId]
                ?: throw CouponNotFoundException()
            UserCouponResponse.from(userCoupon, coupon)
        }

        return SliceImpl(responses, pageable, userCouponSlice.hasNext())
    }
}
