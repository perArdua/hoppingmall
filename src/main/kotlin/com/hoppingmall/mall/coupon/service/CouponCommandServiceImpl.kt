package com.hoppingmall.mall.coupon.service

import com.hoppingmall.mall.coupon.domain.Coupon
import com.hoppingmall.mall.coupon.domain.UserCoupon
import com.hoppingmall.mall.coupon.domain.repository.CouponRepository
import com.hoppingmall.mall.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.mall.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse
import com.hoppingmall.mall.coupon.enum.CouponStatus
import com.hoppingmall.mall.coupon.enum.UserCouponStatus
import com.hoppingmall.mall.coupon.exception.*
import com.hoppingmall.mall.global.common.lock.DistributedLockExecutor
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class CouponCommandServiceImpl(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val distributedLockExecutor: DistributedLockExecutor
) : CouponCommandService {

    @Caching(evict = [
        CacheEvict(cacheNames = ["coupon:available"], allEntries = true),
        CacheEvict(cacheNames = ["coupon:all"], allEntries = true)
    ])
    override fun createCoupon(request: CouponCreateRequest): CouponResponse {
        val coupon = Coupon.create(
            name = request.name,
            code = request.code,
            discountType = request.discountType,
            discountValue = request.discountValue,
            minOrderAmount = request.minOrderAmount,
            maxDiscountAmount = request.maxDiscountAmount,
            totalQuantity = request.totalQuantity,
            validFrom = request.validFrom,
            validTo = request.validTo
        )
        val savedCoupon = couponRepository.save(coupon)
        return CouponResponse.from(savedCoupon)
    }

    @Caching(evict = [
        CacheEvict(cacheNames = ["coupon:available"], allEntries = true),
        CacheEvict(cacheNames = ["coupon:all"], allEntries = true)
    ])
    override fun changeCouponStatus(couponId: Long, status: CouponStatus): CouponResponse {
        val coupon = couponRepository.findById(couponId)
            .orElseThrow { CouponNotFoundException() }
        coupon.changeStatus(status)
        return CouponResponse.from(couponRepository.save(coupon))
    }

    @CacheEvict(cacheNames = ["coupon:available"], allEntries = true)
    override fun issueCoupon(userId: Long, couponId: Long): UserCouponResponse {
        return distributedLockExecutor.withLock("coupon:issue:$couponId") {
            val coupon = couponRepository.findActiveById(couponId)
                ?: throw CouponNotFoundException()

            if (coupon.isExpired()) {
                throw CouponExpiredException()
            }

            if (coupon.isExhausted()) {
                throw CouponExhaustedException()
            }

            if (!coupon.isValid()) {
                throw CouponNotAvailableException()
            }

            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw CouponAlreadyIssuedException()
            }

            coupon.issue()
            couponRepository.save(coupon)

            val userCoupon = UserCoupon.create(userId = userId, couponId = couponId)
            val savedUserCoupon = userCouponRepository.save(userCoupon)

            UserCouponResponse.from(savedUserCoupon, coupon)
        }
    }

    override fun useCoupon(userId: Long, couponId: Long, orderAmount: BigDecimal, orderId: Long): BigDecimal {
        val userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?: throw CouponNotFoundException()

        if (userCoupon.status != UserCouponStatus.ISSUED) {
            throw CouponNotAvailableException()
        }

        val coupon = couponRepository.findById(couponId)
            .orElseThrow { CouponNotFoundException() }

        if (!coupon.isValid()) {
            throw CouponExpiredException()
        }

        if (orderAmount < coupon.minOrderAmount) {
            throw CouponMinAmountNotMetException()
        }

        val discountAmount = coupon.calculateDiscount(orderAmount)
        userCoupon.use(orderId)
        userCouponRepository.save(userCoupon)

        return discountAmount
    }

    override fun restoreCouponByPayment(couponId: Long, userId: Long) {
        val userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?: return
        if (userCoupon.status == UserCouponStatus.USED) {
            userCoupon.restore()
            userCouponRepository.save(userCoupon)
        }
    }

    override fun restoreCouponByOrder(orderId: Long) {
        val userCoupon = userCouponRepository.findByOrderId(orderId)
            ?: return
        if (userCoupon.status == UserCouponStatus.USED) {
            userCoupon.restore()
            userCouponRepository.save(userCoupon)
        }
    }
}
