package com.hoppingmall.payment.coupon.infrastructure

import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.service.CouponCommandServiceImpl
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["coupon.reconciliation.enabled"], havingValue = "true")
class CouponStockReconciliationScheduler(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val couponStockRedisRepository: CouponStockRedisRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${coupon.reconciliation.min-reservation-age-ms:300000}")
    private var minReservationAgeMs: Long = DEFAULT_MIN_RESERVATION_AGE_MS

    @PostConstruct
    fun validateMinReservationAge() {
        require(minReservationAgeMs > CouponCommandServiceImpl.ISSUE_TRANSACTION_TIMEOUT_SECONDS * 1000L) {
            "coupon.reconciliation.min-reservation-age-ms는 발급 트랜잭션 제한 시간" +
                "(${CouponCommandServiceImpl.ISSUE_TRANSACTION_TIMEOUT_SECONDS}초)보다 길어야 한다: $minReservationAgeMs"
        }
    }

    @Scheduled(fixedDelayString = "\${coupon.reconciliation.interval-ms:600000}")
    fun reconcile() {
        val activeCoupons = couponRepository.findAllActive()
        if (activeCoupons.isEmpty()) return

        var totalGhosts = 0
        activeCoupons.forEach { coupon ->
            val couponId = coupon.id ?: return@forEach
            totalGhosts += reconcileCoupon(couponId)
        }

        if (totalGhosts > 0) {
            log.warn("Coupon Redis 정합성 스윕 완료: 총 고스트 {} 건 보정", totalGhosts)
        }
    }

    private fun reconcileCoupon(couponId: Long): Int {
        val redisIssuedUserIds = runCatching { couponStockRedisRepository.getIssuedUserIds(couponId) }
            .getOrElse { e ->
                log.error("Redis issued set 조회 실패: couponId={}", couponId, e)
                return 0
            }
        if (redisIssuedUserIds.isEmpty()) return 0

        val reservedBeforeMillis = runCatching { couponStockRedisRepository.reservationClockMillis(couponId) }
            .getOrElse { e ->
                log.error("Redis 시각 조회 실패: couponId={}", couponId, e)
                return 0
            } - minReservationAgeMs

        val dbIssuedUserIds = userCouponRepository.findUserIdsByCouponId(couponId).toSet()
        val candidateUserIds = redisIssuedUserIds - dbIssuedUserIds
        if (candidateUserIds.isEmpty()) return 0

        val recentUserIds = runCatching {
            couponStockRedisRepository.findReservedAfter(couponId, candidateUserIds, reservedBeforeMillis)
        }.getOrElse { e ->
            log.error("Redis 예약 시각 조회 실패: couponId={}", couponId, e)
            return 0
        }
        val ghostUserIds = candidateUserIds - recentUserIds
        if (ghostUserIds.isEmpty()) return 0

        log.warn("Coupon Redis 고스트 예약 발견: couponId={}, count={}", couponId, ghostUserIds.size)

        var restored = 0
        ghostUserIds.forEach { userId ->
            try {
                couponStockRedisRepository.restoreStock(couponId, userId)
                restored++
            } catch (e: Exception) {
                log.error("Redis 고스트 복원 실패: couponId={}, userId={}", couponId, userId, e)
            }
        }
        return restored
    }

    companion object {
        const val DEFAULT_MIN_RESERVATION_AGE_MS = 300_000L
    }
}
