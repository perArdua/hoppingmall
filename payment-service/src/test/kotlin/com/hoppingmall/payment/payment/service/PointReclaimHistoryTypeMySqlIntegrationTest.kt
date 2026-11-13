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
import com.hoppingmall.payment.point.service.PointCommandService
import com.hoppingmall.payment.point.service.PointCommandServiceImpl
import com.hoppingmall.payment.point.service.PointDomainService
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("적립 포인트 회수 이력의 타입·부호 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(RefundPointsService::class, PointDomainService::class, PointCommandServiceImpl::class)
class PointReclaimHistoryTypeMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var refundPointsService: RefundPointsService

    @Autowired
    private lateinit var pointCommandService: PointCommandService

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @BeforeEach
    fun cleanPointTables() {
        listOf("point_histories", "points", "payments").forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 환불의_적립_포인트_회수_이력은_반환과_다른_회수_타입에_음수_금액으로_남는다() {
        val payment = savedSucceededPayment()
        earn(payment, BigDecimal("300"))

        refundPointsService.reclaimForRefund(USER_ID, payment.id!!, refundId = 1L, refundAmount = BigDecimal("10000"), isFullRefund = false)

        assertThat(history("refund-${payment.id}-1")).isEqualTo("RECLAIM" to BigDecimal("-100.00"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal("200"))
    }

    @Test
    fun 결제_취소의_적립_포인트_회수_이력도_회수_타입에_음수_금액으로_남는다() {
        val payment = savedSucceededPayment()
        earn(payment, BigDecimal("300"))

        refundPointsService.refundPoints(USER_ID, payment.id!!)

        assertThat(history("refund-${payment.id}")).isEqualTo("RECLAIM" to BigDecimal("-300.00"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 환불로_포인트를_돌려주는_이력은_지금처럼_반환_타입에_양수_금액으로_남는다() {
        val payment = savedSucceededPayment()

        pointCommandService.refundPoints(USER_ID, BigDecimal("500"), payment.id!!, payment.orderId)

        assertThat(history("refund-point-${payment.id}-${payment.orderId}")).isEqualTo("REFUND" to BigDecimal("500.00"))
    }

    @Test
    fun 이전_형식으로_남은_회수_이력도_회수한_것으로_보고_전체_환불은_남은_적립만_회수한다() {
        val payment = savedSucceededPayment()
        earn(payment, BigDecimal("300"))
        jdbcTemplate.update("UPDATE points SET balance = 1200 WHERE user_id = ?", USER_ID)
        jdbcTemplate.update(
            "INSERT INTO point_histories (user_id, amount, type, reason, payment_id, event_id, created_at) VALUES (?, ?, 'REFUND', ?, ?, ?, NOW(6))",
            USER_ID, BigDecimal("100"), "환불 포인트 회수", payment.id, "refund-${payment.id}-1"
        )

        refundPointsService.reclaimForRefund(USER_ID, payment.id!!, refundId = 2L, refundAmount = BigDecimal("20000"), isFullRefund = true)

        assertThat(history("refund-${payment.id}-2")!!.second.abs()).isEqualByComparingTo(BigDecimal("200"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal("1000"))
    }

    private fun savedSucceededPayment(): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = USER_ID,
            amount = BigDecimal("30000"),
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "tx-721", completedAt = LocalDateTime.now())
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

    private fun history(eventId: String): Pair<String, BigDecimal>? =
        jdbcTemplate.query(
            "SELECT type, amount FROM point_histories WHERE event_id = ?",
            { rs, _ -> rs.getString("type") to rs.getBigDecimal("amount") },
            eventId
        ).firstOrNull()

    private fun balance(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT balance FROM points WHERE user_id = ?", BigDecimal::class.java, USER_ID)!!

    companion object {
        private const val USER_ID = 721L
        private const val ORDER_ID = 7210L
    }
}
