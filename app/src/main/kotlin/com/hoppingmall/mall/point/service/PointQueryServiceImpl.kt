package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.dto.response.PointBalanceResponse
import com.hoppingmall.mall.point.dto.response.PointHistoryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class PointQueryServiceImpl(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository
) : PointQueryService {
    
    override fun getPointBalance(userId: Long): PointBalanceResponse {
        val point = pointRepository.findByUserId(userId)
        val balance = point?.balance ?: BigDecimal.ZERO
        return PointBalanceResponse(balance = balance)
    }
    
    override fun getPointHistory(userId: Long, pageable: Pageable): Page<PointHistoryResponse> {
        return pointHistoryRepository.findByUserId(userId, pageable)
            .map { history ->
                PointHistoryResponse(
                    id = history.id!!,
                    userId = history.userId,
                    amount = history.amount,
                    type = history.type,
                    reason = history.reason,
                    orderId = history.orderId,
                    paymentId = history.paymentId,
                    createdAt = history.createdAt
                )
            }
    }
} 