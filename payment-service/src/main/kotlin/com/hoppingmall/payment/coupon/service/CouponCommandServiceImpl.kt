package com.hoppingmall.payment.coupon.service

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.payment.coupon.dto.response.CouponResponse
import com.hoppingmall.payment.coupon.dto.response.UserCouponResponse
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import com.hoppingmall.payment.coupon.exception.*
import com.hoppingmall.payment.internal.DistributedLockExecutor
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class CouponCommandServiceImpl(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val distributedLockExecutor: DistributedLockExecutor
) : CouponCommandService {

    private val log = LoggerFactory.getLogger(javaClass)

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

            log.info("쿠폰 발급: userId={}, couponId={}, remaining={}", userId, couponId, coupon.totalQuantity - coupon.issuedQuantity)
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
