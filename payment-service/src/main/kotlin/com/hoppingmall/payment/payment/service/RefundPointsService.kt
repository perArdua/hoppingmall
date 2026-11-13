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
import java.math.RoundingMode

@Service
class RefundPointsService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val paymentRepository: PaymentRepository?
) {

    @Transactional
    fun refundPoints(userId: Long, paymentId: Long) {
        val earnHistory = findEarnHistory(paymentId) ?: return
        reclaim(
            userId = userId,
            paymentId = paymentId,
            target = remainingEarned(paymentId, earnHistory),
            eventId = cancelEventId(paymentId),
            reason = "결제 취소 포인트 반환"
        )
    }

    @Transactional
    fun reclaimForRefund(userId: Long, paymentId: Long, refundId: Long, refundAmount: BigDecimal, isFullRefund: Boolean) {
        val eventId = refundEventIdPrefix(paymentId) + refundId
        if (pointHistoryRepository.existsByEventId(eventId)) return

        val earnHistory = findEarnHistory(paymentId) ?: return
        val remaining = remainingEarned(paymentId, earnHistory)
        val target = if (isFullRefund) {
            remaining
        } else {
            proportionalEarned(paymentId, earnHistory.amount, refundAmount).min(remaining)
        }

        reclaim(
            userId = userId,
            paymentId = paymentId,
            target = target,
            eventId = eventId,
            reason = "환불 포인트 회수"
        )
    }

    private fun proportionalEarned(paymentId: Long, earnedAmount: BigDecimal, refundAmount: BigDecimal): BigDecimal {
        val payment = paymentRepository?.findByIdOrNull(paymentId) ?: return BigDecimal.ZERO
        if (payment.amount <= BigDecimal.ZERO) return BigDecimal.ZERO
        return earnedAmount.multiply(refundAmount).divide(payment.amount, 2, RoundingMode.DOWN)
    }

    private fun findEarnHistory(paymentId: Long): PointHistory? {
        val earnHistory = pointHistoryRepository.findByPaymentIdAndType(paymentId, PointType.EARN)
        if (earnHistory == null) {
            check(!isEarnRequested(paymentId)) { "적립 이력이 아직 없어 포인트를 회수할 수 없음: paymentId=$paymentId" }
        }
        return earnHistory
    }

    private fun remainingEarned(paymentId: Long, earnHistory: PointHistory): BigDecimal {
        val reclaimed = pointHistoryRepository.findAllByPaymentIdAndType(paymentId, PointType.REFUND)
            .filter { it.eventId == cancelEventId(paymentId) || it.eventId?.startsWith(refundEventIdPrefix(paymentId)) == true }
            .sumOf { it.amount }
        return earnHistory.amount.subtract(reclaimed).max(BigDecimal.ZERO)
    }

    private fun reclaim(userId: Long, paymentId: Long, target: BigDecimal, eventId: String, reason: String) {
        val point = pointRepository.findByUserIdForUpdate(userId) ?: return

        val reclaimed = target.min(point.balance).max(BigDecimal.ZERO)
        val shortfall = target.subtract(reclaimed)
        point.usePoints(reclaimed)
        pointRepository.save(point)

        pointHistoryRepository.save(
            PointHistory(
                userId = userId,
                amount = reclaimed,
                type = PointType.REFUND,
                reason = if (shortfall > BigDecimal.ZERO) "$reason (잔액 부족으로 ${shortfall.toPlainString()} 미회수)" else reason,
                paymentId = paymentId,
                eventId = eventId
            )
        )
    }

    private fun isEarnRequested(paymentId: Long): Boolean {
        val payment = paymentRepository?.findByIdOrNull(paymentId) ?: return false
        return payment.transactionId != null && payment.amount > BigDecimal.ZERO
    }

    private fun cancelEventId(paymentId: Long) = "refund-$paymentId"

    private fun refundEventIdPrefix(paymentId: Long) = "refund-$paymentId-"
}
