package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.strategy.PaymentCancelledCompensationHandler
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

@DisplayName("환불·결제 취소의 적립 포인트 회수 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(RefundPointsService::class, CompensationEventLogService::class)
class RefundPointReclaimMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var refundPointsService: RefundPointsService

    @Autowired
    private lateinit var compensationEventLogService: CompensationEventLogService

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

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @BeforeEach
    fun cleanPointTables() {
        listOf("refund_event_log_steps", "refund_event_logs", "compensation_event_logs", "point_histories", "points", "payments")
            .forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 전체_환불은_결제의_적립_포인트를_모두_회수한다() {
        val payment = savedSucceededPayment(BigDecimal("30000"), "tx-561-1")
        earn(payment, BigDecimal("300"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("30000"), isFullRefund = true)

        assertThat(balance()).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("300"))
    }

    @Test
    fun 부분_환불은_환불액_비율만큼_적립_포인트를_소수_둘째_자리_아래를_버려_회수한다() {
        val payment = savedSucceededPayment(BigDecimal("30000"), "tx-561-2")
        earn(payment, BigDecimal("301"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("10000"), isFullRefund = false)

        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("100.33"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal("200.67"))
    }

    @Test
    fun 쿠폰_결제의_부분_환불_비율은_쿠폰_할인_전_금액을_분모로_한다() {
        val payment = savedSucceededPayment(BigDecimal("27000"), "tx-561-3", couponDiscountAmount = BigDecimal("3000"))
        earn(payment, BigDecimal("270"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("15000"), isFullRefund = false)

        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("135"))
    }

    @Test
    fun 부분_환불_두_번은_환불마다_따로_회수되고_마지막_전체_환불이_남은_적립을_모두_회수한다() {
        val payment = savedSucceededPayment(BigDecimal("30000"), "tx-561-4")
        earn(payment, BigDecimal("301"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("10000"), isFullRefund = false)
        refund(payment, refundId = 2L, refundAmount = BigDecimal("10000"), isFullRefund = false)
        refund(payment, refundId = 3L, refundAmount = BigDecimal("10000"), isFullRefund = true)

        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("100.33"))
        assertThat(reclaimedAmount("refund-${payment.id}-2")).isEqualByComparingTo(BigDecimal("100.33"))
        assertThat(reclaimedAmount("refund-${payment.id}-3")).isEqualByComparingTo(BigDecimal("100.34"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 같은_환불이_다시_처리되어도_적립_포인트는_한_번만_회수한다() {
        val payment = savedSucceededPayment(BigDecimal("30000"), "tx-561-5")
        earn(payment, BigDecimal("300"))
        jdbcTemplate.update("UPDATE points SET balance = balance + 1000 WHERE user_id = ?", USER_ID)

        refund(payment, refundId = 1L, refundAmount = BigDecimal("10000"), isFullRefund = false)
        refund(payment, refundId = 1L, refundAmount = BigDecimal("10000"), isFullRefund = false, eventId = "refund-completed-1-again")

        assertThat(balance()).isEqualByComparingTo(BigDecimal("1200"))
        assertThat(reclaimHistoryCount(payment)).isEqualTo(1L)
    }

    @Test
    fun 적립_포인트를_쓴_뒤_환불되면_남은_잔액만_회수하고_부족분을_이력_사유에_남긴다() {
        val payment = savedSucceededPayment(BigDecimal("30000"), "tx-561-6")
        earn(payment, BigDecimal("300"))
        jdbcTemplate.update("UPDATE points SET balance = 50 WHERE user_id = ?", USER_ID)

        val result = runCatching { refund(payment, refundId = 1L, refundAmount = BigDecimal("30000"), isFullRefund = true) }

        assertThat(result.exceptionOrNull()).isNull()
        assertThat(balance()).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("50"))
        assertThat(reclaimReason("refund-${payment.id}-1")).contains("250")
    }

    @Test
    fun 적립_포인트를_쓴_뒤_결제가_취소되면_예외_없이_남은_잔액만_회수하고_부족분을_이력_사유에_남긴다() {
        val payment = savedSucceededPayment(BigDecimal("30000"), "tx-561-7")
        earn(payment, BigDecimal("300"))
        jdbcTemplate.update("UPDATE points SET balance = 50 WHERE user_id = ?", USER_ID)
        cancel(payment)

        val result = runCatching { cancelledHandler().handle(paymentCancelledNode(payment)) }

        assertThat(result.exceptionOrNull()).isNull()
        assertThat(compensationStatus("payment-cancelled-${payment.id}")).isEqualTo("COMPLETED")
        assertThat(balance()).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(reclaimedAmount("refund-${payment.id}")).isEqualByComparingTo(BigDecimal("50"))
        assertThat(reclaimReason("refund-${payment.id}")).contains("250")
    }

    @Test
    fun 부분_환불_뒤_결제가_취소되면_취소는_아직_회수하지_않은_적립만_회수한다() {
        val payment = savedSucceededPayment(BigDecimal("30000"), "tx-561-8")
        earn(payment, BigDecimal("300"))
        jdbcTemplate.update("UPDATE points SET balance = balance + 1000 WHERE user_id = ?", USER_ID)

        refund(payment, refundId = 1L, refundAmount = BigDecimal("10000"), isFullRefund = false)
        cancel(payment)
        cancelledHandler().handle(paymentCancelledNode(payment))

        assertThat(reclaimedAmount("refund-${payment.id}-1")).isEqualByComparingTo(BigDecimal("100"))
        assertThat(reclaimedAmount("refund-${payment.id}")).isEqualByComparingTo(BigDecimal("200"))
        assertThat(balance()).isEqualByComparingTo(BigDecimal("1000"))
    }

    private fun refund(
        payment: Payment,
        refundId: Long,
        refundAmount: BigDecimal,
        isFullRefund: Boolean,
        eventId: String = "refund-completed-$refundId"
    ) {
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
            mock()
        )
        TransactionTemplate(transactionManager).executeWithoutResult {
            val eventLog = refundEventLogRepository.save(
                RefundEventLog(eventId = eventId, eventType = "REFUND_COMPLETED", refundId = refundId, orderId = payment.orderId)
            )
            service.execute(event, eventLog)
        }
    }

    private fun cancelledHandler() =
        PaymentCancelledCompensationHandler(compensationEventLogService, refundPointsService, objectMapper)

    private fun savedSucceededPayment(
        amount: BigDecimal,
        transactionId: String,
        couponDiscountAmount: BigDecimal = BigDecimal.ZERO
    ): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = USER_ID,
            amount = amount,
            method = PaymentMethod.CREDIT_CARD,
            couponId = if (couponDiscountAmount > BigDecimal.ZERO) 1L else null,
            couponDiscountAmount = couponDiscountAmount
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = transactionId, completedAt = LocalDateTime.now())
        return paymentRepository.save(payment)
    }

    private fun cancel(payment: Payment) {
        val stored = paymentRepository.findById(payment.id!!).get()
        stored.updateStatus(PaymentStatus.CANCELLED, transactionId = stored.transactionId, completedAt = stored.completedAt)
        paymentRepository.save(stored)
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

    private fun paymentCancelledNode(payment: Payment) =
        objectMapper.valueToTree<JsonNode>(
            mapOf(
                "eventType" to "PaymentCancelled",
                "eventId" to "payment-cancelled-${payment.id}",
                "paymentId" to payment.id,
                "orderId" to payment.orderId,
                "userId" to payment.userId,
                "amount" to payment.amount,
                "transactionId" to payment.transactionId
            )
        )

    private fun balance(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT balance FROM points WHERE user_id = ?", BigDecimal::class.java, USER_ID)!!

    private fun reclaimedAmount(eventId: String): BigDecimal? =
        jdbcTemplate.queryForList(
            "SELECT amount FROM point_histories WHERE event_id = ? AND type = 'REFUND'",
            BigDecimal::class.java,
            eventId
        ).firstOrNull()

    private fun reclaimReason(eventId: String): String =
        jdbcTemplate.queryForObject("SELECT reason FROM point_histories WHERE event_id = ?", String::class.java, eventId)!!

    private fun reclaimHistoryCount(payment: Payment): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM point_histories WHERE payment_id = ? AND type = 'REFUND'",
            Long::class.java,
            payment.id
        )!!

    private fun compensationStatus(eventId: String): String? =
        jdbcTemplate.queryForList("SELECT status FROM compensation_event_logs WHERE event_id = ?", String::class.java, eventId)
            .firstOrNull()

    companion object {
        private const val USER_ID = 561L
        private const val ORDER_ID = 5610L
    }
}
