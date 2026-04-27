package com.hoppingmall.payment.coupon.service

import org.springframework.data.repository.findByIdOrNull
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
import com.hoppingmall.payment.coupon.infrastructure.CouponReserveResult
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Profile("!coupon-naive")
@Transactional
class CouponCommandServiceImpl(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val couponStockRedisRepository: CouponStockRedisRepository
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
        try {
            couponStockRedisRepository.initializeStock(savedCoupon.id!!, savedCoupon.totalQuantity)
        } catch (e: Exception) {
            log.warn("Redis 재고 초기화 실패 (lazy init 폴백): couponId={}", savedCoupon.id, e)
        }
        return CouponResponse.from(savedCoupon)
    }

    @Caching(evict = [
        CacheEvict(cacheNames = ["coupon:available"], allEntries = true),
        CacheEvict(cacheNames = ["coupon:all"], allEntries = true)
    ])
    override fun changeCouponStatus(couponId: Long, status: CouponStatus): CouponResponse {
        val coupon = couponRepository.findByIdOrNull(couponId) ?: throw CouponNotFoundException()
        coupon.changeStatus(status)
        val saved = couponRepository.save(coupon)
        try {
            if (status == CouponStatus.INACTIVE) {
                couponStockRedisRepository.deleteStock(couponId)
            } else if (status == CouponStatus.ACTIVE) {
                couponStockRedisRepository.initializeStock(couponId, saved.totalQuantity - saved.issuedQuantity)
            }
        } catch (e: Exception) {
            log.warn("Redis 재고 상태 변경 실패: couponId={}, status={}", couponId, status, e)
        }
        return CouponResponse.from(saved)
    }

    @CacheEvict(cacheNames = ["coupon:available"], allEntries = true)
    override fun issueCoupon(userId: Long, couponId: Long): UserCouponResponse {
        var result = couponStockRedisRepository.tryReserve(couponId, userId)

        if (result is CouponReserveResult.NotInitialized) {
            val coupon = couponRepository.findActiveById(couponId)
                ?: throw CouponNotFoundException()
            couponStockRedisRepository.initializeStock(couponId, coupon.totalQuantity - coupon.issuedQuantity)
            result = couponStockRedisRepository.tryReserve(couponId, userId)
        }

        when (result) {
            is CouponReserveResult.Exhausted -> throw CouponExhaustedException()
            is CouponReserveResult.AlreadyIssued -> throw CouponAlreadyIssuedException()
            is CouponReserveResult.NotInitialized -> throw CouponNotFoundException()
            is CouponReserveResult.Success -> {}
        }

        val coupon = couponRepository.findActiveById(couponId)
            ?: run {
                couponStockRedisRepository.restoreStock(couponId, userId)
                throw CouponNotFoundException()
            }

        if (coupon.isExpired() || !coupon.isValid()) {
            couponStockRedisRepository.restoreStock(couponId, userId)
            throw CouponExpiredException()
        }

        var restored = false
        try {
            val updated = couponRepository.incrementIssuedQuantity(couponId)
            if (updated == 0) {
                couponStockRedisRepository.restoreStock(couponId, userId)
                restored = true
                throw CouponExhaustedException()
            }
            val userCoupon = UserCoupon.create(userId = userId, couponId = couponId)
            val savedUserCoupon = userCouponRepository.save(userCoupon)
            log.info("쿠폰 발급: userId={}, couponId={}", userId, couponId)
            return UserCouponResponse.from(savedUserCoupon, coupon)
        } catch (e: Exception) {
            if (!restored) {
                try {
                    couponStockRedisRepository.restoreStock(couponId, userId)
                } catch (re: Exception) {
                    log.error("Redis 재고 복원 실패: couponId={}, userId={}", couponId, userId, re)
                }
            }
            throw e
        }
    }

    override fun useCoupon(userId: Long, couponId: Long, orderAmount: BigDecimal, orderId: Long): BigDecimal {
        val userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?: throw CouponNotFoundException()

        if (userCoupon.status != UserCouponStatus.ISSUED) {
            throw CouponNotAvailableException()
        }

        val coupon = couponRepository.findByIdOrNull(couponId) ?: throw CouponNotFoundException() 

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
