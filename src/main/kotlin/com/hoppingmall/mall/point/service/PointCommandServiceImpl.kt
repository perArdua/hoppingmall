package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.dto.request.PointUseRequest
import com.hoppingmall.mall.point.dto.response.PointUseResponse
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.point.exception.PointInsufficientBalanceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class PointCommandServiceImpl(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository
) : PointCommandService {
    
    override fun usePoint(userId: Long, request: PointUseRequest): PointUseResponse {
        val point = findOrCreatePoint(userId)
        validateSufficientBalance(point, request.amount)
        
        // 포인트 차감
        point.balance = point.balance.subtract(request.amount)
        val savedPoint = pointRepository.save(point)
        
        // 포인트 사용 내역 기록
        val pointHistory = PointHistory(
            userId = userId,
            amount = request.amount.negate(), // 음수로 기록 (사용)
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
    
    private fun validateSufficientBalance(point: Point, useAmount: BigDecimal) {
        if (point.balance < useAmount) {
            throw PointInsufficientBalanceException()
        }
    }
    
    private fun findOrCreatePoint(userId: Long): Point {
        return try {
            pointRepository.findByUserId(userId)
                ?: run {
                    val newPoint = Point(userId = userId)
                    pointRepository.save(newPoint)
                }
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            pointRepository.findByUserId(userId)
                ?: throw IllegalStateException("포인트 생성 실패: 사용자 $userId")
        }
    }
} 