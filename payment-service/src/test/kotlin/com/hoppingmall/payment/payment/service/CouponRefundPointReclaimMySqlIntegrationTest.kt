package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.payment.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.payment.refund.service.RefundLocalOperationService
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("쿠폰 결제의 부분 환불 적립 포인트 회수 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(RefundPointsService::class)
class CouponRefundPointReclaimMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var refundPointsService: RefundPointsService

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Autowired
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanPointTables() {
        listOf("refund_event_log_steps", "refund_event_logs", "point_histories", "points", "payments")
            .forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 쿠폰_결제의_부분_환불은_실제_결제_금액_대비_환불_금액_비율만큼_적립을_회수한다() {
        val payment = savedCouponPayment(amount = BigDecimal("7000"), couponDiscountAmount = BigDecimal("3000"), transactionId = "tx-736-1")
        earn(payment, BigDecimal("70"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("3500"), isFullRefund = false)

        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("35"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal("35"))
    }

    @Test
    fun 쿠폰_결제를_여러_번_부분_환불해_전부_환불되면_환불마다_같은_비율로_회수하고_합계가_적립_전액이다() {
        val payment = savedCouponPayment(amount = BigDecimal("27000"), couponDiscountAmount = BigDecimal("3000"), transactionId = "tx-736-2")
        earn(payment, BigDecimal("270"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("9000"), isFullRefund = false)
        refund(payment, refundId = 2L, refundAmount = BigDecimal("9000"), isFullRefund = false)
        refund(payment, refundId = 3L, refundAmount = BigDecimal("9000"), isFullRefund = true)

        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("90"))
        assertThat(reclaimedAmount("refund-${payment.id}-2")).isEqualByComparingTo(BigDecimal("90"))
        assertThat(reclaimedAmount("refund-${payment.id}-3")).isEqualByComparingTo(BigDecimal("90"))
        assertThat(totalReclaimed(payment)).isEqualByComparingTo(BigDecimal("270"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal.ZERO)
    }

    private fun refund(payment: Payment, refundId: Long, refundAmount: BigDecimal, isFullRefund: Boolean) {
        val eventId = "refund-completed-$refundId"
        val event = RefundCompletedEvent(
            eventId = eventId,
            refundId = refundId,
            orderId = payment.orderId,
            paymentId = payment.id!!,
            buyerId = USER_ID,
            refundAmount = refundAmount,
            pointRefundAmount = BigDecimal.ZERO,
            isFullRefund = isFullRefund,
            couponId = null,
            items = emptyList()
        )
        val service = RefundLocalOperationService(
            paymentRepository,
            mock(),
            mock(),
            refundEventLogRepository,
            refundPointsService,
            mock(),
            mock()
        )
        TransactionTemplate(transactionManager).executeWithoutResult {
            val eventLog = refundEventLogRepository.save(
                RefundEventLog(eventId = eventId, eventType = "REFUND_COMPLETED", refundId = refundId, orderId = payment.orderId)
            )
            service.execute(event, eventLog)
        }
    }

    private fun savedCouponPayment(amount: BigDecimal, couponDiscountAmount: BigDecimal, transactionId: String): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = USER_ID,
            amount = amount,
            method = PaymentMethod.CREDIT_CARD,
            couponId = 1L,
            couponDiscountAmount = couponDiscountAmount
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = transactionId, completedAt = LocalDateTime.now())
        return paymentRepository.save(payment)
    }

    private fun earn(payment: Payment, earnAmount: BigDecimal) {
        pointRepository.save(Point(userId = USER_ID, balance = earnAmount))
        pointHistoryRepository.save(
            PointHistory(
                userId = USER_ID,
                amount = earnAmount,
                type = PointType.EARN,
                reason = "결제 완료 적립",
                orderId = payment.orderId,
                paymentId = payment.id,
                eventId = payment.transactionId
            )
        )
    }

    private fun balance(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT balance FROM points WHERE user_id = ?", BigDecimal::class.java, USER_ID)!!

    private fun reclaimedAmount(eventId: String): BigDecimal? =
        jdbcTemplate.queryForList(
            "SELECT -amount FROM point_histories WHERE event_id = ? AND type = 'RECLAIM'",
            BigDecimal::class.java,
            eventId
        ).firstOrNull()

    private fun totalReclaimed(payment: Payment): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(-amount), 0) FROM point_histories WHERE payment_id = ? AND type = 'RECLAIM'",
            BigDecimal::class.java,
            payment.id
        )!!

    companion object {
        private const val USER_ID = 736L
        private const val ORDER_ID = 7360L
    }
}
