package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.domain.Point
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
    private val pointHistoryRepository: PointHistoryRepository
) : PointCommandService {

    @CacheEvict(cacheNames = ["point-balance"], key = "#userId")
    override fun usePoint(userId: Long, request: PointUseRequest): PointUseResponse {
        val point = findOrCreatePoint(userId)
        validateSufficientBalance(point, request.amount)

        point.balance = point.balance.subtract(request.amount)
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
    override fun refundPoints(userId: Long, amount: BigDecimal, paymentId: Long, orderId: Long) {
        if (amount <= BigDecimal.ZERO) return

        val eventId = "refund-point-$paymentId-$orderId"
        if (pointHistoryRepository.existsByEventId(eventId)) return

        val point = findOrCreatePoint(userId)
        point.balance = point.balance.add(amount)
        pointRepository.save(point)

        val pointHistory = PointHistory(
            userId = userId,
            amount = amount,
            type = PointType.REFUND,
            reason = "환불 포인트 반환",
            orderId = orderId,
            paymentId = paymentId,
            eventId = eventId
        )
        pointHistoryRepository.save(pointHistory)
    }

    private fun validateSufficientBalance(point: Point, useAmount: BigDecimal) {
        if (point.balance < useAmount) {
            throw PointInsufficientBalanceException()
        }
    }

    private fun findOrCreatePoint(userId: Long): Point {
        return try {
            pointRepository.findByUserIdForUpdate(userId)
                ?: run {
                    val newPoint = Point(userId = userId)
                    pointRepository.save(newPoint)
                }
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            pointRepository.findByUserIdForUpdate(userId)
                ?: throw IllegalStateException("포인트 생성 실패: 사용자 $userId")
        }
    }
}
