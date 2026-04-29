package com.hoppingmall.payment.coupon.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.payment.coupon.dto.response.CouponResponse
import com.hoppingmall.payment.coupon.dto.response.UserCouponResponse
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import com.hoppingmall.payment.coupon.exception.*
import com.hoppingmall.payment.coupon.infrastructure.CouponReserveResult
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal

@Service
@Profile("!coupon-naive")
@Transactional
class CouponCommandServiceImpl(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val couponStockRedisRepository: CouponStockRedisRepository,
    private val compensationPublisher: CouponCompensationPublisher,
    private val compensationMetrics: CouponCompensationMetrics
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

        registerStockRestoreOnRollback(couponId, userId)

        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CouponNotFoundException()

        if (coupon.isExpired() || !coupon.isValid()) {
            throw CouponExpiredException()
        }

        val updated = couponRepository.incrementIssuedQuantity(couponId)
        if (updated == 0) {
            throw CouponExhaustedException()
        }

        val userCoupon = UserCoupon.create(userId = userId, couponId = couponId)
        val savedUserCoupon = userCouponRepository.save(userCoupon)
        log.info("쿠폰 발급: userId={}, couponId={}", userId, couponId)
        return UserCouponResponse.from(savedUserCoupon, coupon)
    }

    private fun registerStockRestoreOnRollback(couponId: Long, userId: Long) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("트랜잭션 동기화 비활성 — Redis 재고 보상 훅 미등록: couponId={}, userId={}", couponId, userId)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_ROLLED_BACK) return
                    try {
                        couponStockRedisRepository.restoreStock(couponId, userId)
                        compensationMetrics.recordSyncSuccess()
                        log.info("Redis 재고 복원 (tx rollback): couponId={}, userId={}", couponId, userId)
                    } catch (e: Exception) {
                        compensationMetrics.recordSyncFailure()
                        log.error(
                            "Redis 재고 복원 실패 (tx rollback) → 보상 큐 발행 시도: couponId={}, userId={}",
                            couponId, userId, e
                        )
                        publishCompensationEvent(couponId, userId, CouponRestoreReason.DB_INSERT_FAILED)
                    }
                }
            }
        )
    }

    private fun publishCompensationEvent(couponId: Long, userId: Long, reason: CouponRestoreReason) {
        try {
            compensationPublisher.publish(
                CouponRestoreEvent(
                    couponId = couponId,
                    userId = userId,
                    reason = reason
                )
            )
        } catch (e: Exception) {
            log.error(
                "보상 큐 발행마저 실패 (수동 개입 필요): couponId={}, userId={}, reason={}",
                couponId, userId, reason, e
            )
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
