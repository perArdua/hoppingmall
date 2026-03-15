package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefundPointsService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository
) {

    @Transactional
    fun refundPoints(userId: Long, paymentId: Long) {
        val earnHistory = pointHistoryRepository.findByPaymentIdAndType(paymentId, PointType.EARN)
            ?: return

        val point = pointRepository.findByUserId(userId) ?: return

        point.usePoints(earnHistory.amount)
        pointRepository.save(point)

        pointHistoryRepository.save(
            PointHistory(
                userId = userId,
                amount = earnHistory.amount,
                type = PointType.REFUND,
                reason = "결제 취소 포인트 반환",
                paymentId = paymentId,
                eventId = "refund-$paymentId"
            )
        )
    }
}
