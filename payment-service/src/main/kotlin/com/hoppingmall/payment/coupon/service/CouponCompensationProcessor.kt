package com.hoppingmall.payment.coupon.service

import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.infrastructure.CouponRestoreResult
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CouponCompensationProcessor(
    private val couponStockRedisRepository: CouponStockRedisRepository,
    private val metrics: CouponCompensationMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun process(event: CouponRestoreEvent): CouponRestoreResult {
        val result = couponStockRedisRepository.restoreStockIdempotent(event.couponId, event.userId)
        when (result) {
            CouponRestoreResult.Restored ->
                log.info(
                    "쿠폰 재고 보상 복원: couponId={}, userId={}, reason={}",
                    event.couponId, event.userId, event.reason
                )
            CouponRestoreResult.AlreadyRestored ->
                log.info(
                    "쿠폰 재고 보상 멱등 스킵 (이미 복원됨): couponId={}, userId={}",
                    event.couponId, event.userId
                )
        }
        metrics.recordAsyncConsumed()
        return result
    }
}
