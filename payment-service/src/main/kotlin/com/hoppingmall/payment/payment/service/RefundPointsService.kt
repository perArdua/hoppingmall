package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class RefundPointsService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val paymentRepository: PaymentRepository?
) {

    @Transactional
    fun refundPoints(userId: Long, paymentId: Long) {
        val earnHistory = pointHistoryRepository.findByPaymentIdAndType(paymentId, PointType.EARN)
        if (earnHistory == null) {
            check(!isEarnRequested(paymentId)) { "적립 이력이 아직 없어 포인트를 회수할 수 없음: paymentId=$paymentId" }
            return
        }

        val point = pointRepository.findByUserIdForUpdate(userId) ?: return

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

    private fun isEarnRequested(paymentId: Long): Boolean {
        val payment = paymentRepository?.findByIdOrNull(paymentId) ?: return false
        return payment.transactionId != null && payment.amount > BigDecimal.ZERO
    }
}
