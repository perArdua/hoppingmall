package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.dto.request.PointUseRequest
import com.hoppingmall.payment.point.dto.response.PointUseResponse
import com.hoppingmall.payment.point.enum.PointType
import com.hoppingmall.payment.point.exception.PointInsufficientBalanceException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class PointCommandServiceImpl(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointDomainService: PointDomainService
) : PointCommandService {

    @CacheEvict(cacheNames = ["point-balance"], key = "#userId")
    override fun usePoint(userId: Long, request: PointUseRequest): PointUseResponse {
        val point = pointDomainService.findOrCreatePoint(userId)
        point.usePoints(request.amount)
        val savedPoint = pointRepository.save(point)

        val pointHistory = PointHistory(
            userId = userId,
            amount = request.amount.negate(),
            type = PointType.USE,
            reason = request.reason ?: "상품 구매",
            orderId = request.orderId,
            paymentId = null
        )
        pointHistoryRepository.save(pointHistory)

        return PointUseResponse(
            usedAmount = request.amount,
            remainingBalance = savedPoint.balance,
            orderId = request.orderId
        )
    }

    @CacheEvict(cacheNames = ["point-balance"], key = "#userId")
    override fun refundPoints(userId: Long, amount: BigDecimal, paymentId: Long, orderId: Long, refundId: Long) {
        if (amount <= BigDecimal.ZERO) return
        restore(userId, paymentId, orderId, "$REFUND_RESTORE_PREFIX$refundId", "환불 포인트 반환", amount)
    }

    @CacheEvict(cacheNames = ["point-balance"], key = "#userId")
    override fun useForPayment(userId: Long, paymentId: Long, orderId: Long, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "사용할 포인트는 0보다 커야 합니다" }
        val point = pointRepository.findByUserIdForUpdate(userId) ?: throw PointInsufficientBalanceException()
        point.usePoints(amount)
        pointRepository.save(point)

        pointHistoryRepository.save(
            PointHistory(
                userId = userId,
                amount = amount.negate(),
                type = PointType.USE,
                reason = "결제 포인트 사용",
                orderId = orderId,
                paymentId = paymentId,
                eventId = "$USE_PREFIX$paymentId"
            )
        )
    }

    @CacheEvict(cacheNames = ["point-balance"], key = "#userId")
    override fun restoreUsedPoints(userId: Long, paymentId: Long, orderId: Long, eventId: String, reason: String): BigDecimal =
        restore(userId, paymentId, orderId, eventId, reason, null)

    private fun restore(
        userId: Long,
        paymentId: Long,
        orderId: Long,
        eventId: String,
        reason: String,
        limit: BigDecimal?
    ): BigDecimal {
        if (pointHistoryRepository.existsByEventId(eventId)) return BigDecimal.ZERO

        val point = pointDomainService.findOrCreatePoint(userId)
        val histories = pointHistoryRepository.findAllByUserIdAndPaymentIdForUpdate(userId, paymentId)
        val used = histories.filter { it.type == PointType.USE }.sumOf { it.amount.abs() }
        val restored = histories.filter { it.isUsedPointRestore() }.sumOf { it.amount }
        val remaining = used.subtract(restored).max(BigDecimal.ZERO)
        val amount = limit?.min(remaining) ?: remaining
        if (amount.signum() <= 0) return BigDecimal.ZERO

        point.addPoints(amount)
        pointRepository.save(point)
        pointHistoryRepository.save(
            PointHistory(
                userId = userId,
                amount = amount,
                type = PointType.REFUND,
                reason = reason,
                orderId = orderId,
                paymentId = paymentId,
                eventId = eventId
            )
        )
        return amount
    }

    private fun PointHistory.isUsedPointRestore(): Boolean =
        type == PointType.REFUND &&
            (eventId?.startsWith(USE_PREFIX) == true || eventId?.startsWith(REFUND_RESTORE_PREFIX) == true)

    companion object {
        const val USE_PREFIX = "use-"
        const val REFUND_RESTORE_PREFIX = "refund-point-"
    }

}
