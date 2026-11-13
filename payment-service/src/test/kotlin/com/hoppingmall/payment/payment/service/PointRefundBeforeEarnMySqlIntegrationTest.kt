package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import com.hoppingmall.payment.payment.service.strategy.PaymentCancelledCompensationHandler
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.service.PointDomainService
import com.hoppingmall.payment.point.service.PointEventConsumer
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("결제 취소 포인트 회수가 적립보다 먼저 처리될 때 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(RefundPointsService::class, CompensationEventLogService::class, PointDomainService::class)
class PointRefundBeforeEarnMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var refundPointsService: RefundPointsService

    @Autowired
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Autowired
    private lateinit var pointDomainService: PointDomainService

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @BeforeEach
    fun cleanPointTables() {
        listOf("compensation_event_logs", "point_histories", "points", "payments")
            .forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 적립_이력이_아직_없는_결제의_취소_회수는_실패로_끝나_완료로_기록되지_않는다() {
        val payment = savedCancelledPayment(BigDecimal("1000"), "tx-549-1")

        val result = runCatching { cancelledHandler().handle(paymentCancelledNode(payment)) }

        assertThat(result.isFailure)
            .describedAs("적립 이력이 없을 때 회수 처리 결과=%s", result)
            .isTrue()
        assertThat(compensationStatus("payment-cancelled-${payment.id}")).isEqualTo("PENDING")
    }

    @Test
    fun 회수가_먼저_실패하고_적립이_들어온_뒤_회수가_다시_처리되면_잔액이_0이다() {
        val payment = savedCancelledPayment(BigDecimal("1000"), "tx-549-2")
        val handler = cancelledHandler()

        runCatching { handler.handle(paymentCancelledNode(payment)) }
        earn(payment, BigDecimal("30"))
        runCatching { handler.handle(paymentCancelledNode(payment)) }

        assertThat(balanceOf(USER_ID)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM point_histories WHERE payment_id = ? AND type = 'REFUND'",
                Long::class.java,
                payment.id
            )
        ).isEqualTo(1L)
        assertThat(compensationStatus("payment-cancelled-${payment.id}")).isEqualTo("COMPLETED")
    }

    @Test
    fun 역보상은_적립_이력이_아직_없어도_포인트를_직접_회수하지_않고_결제_취소를_요청한_뒤_완료로_기록한다() {
        val payment = savedSucceededPayment(BigDecimal("1000"), "tx-549-3")
        val paymentCommandService = mock<PaymentCommandService>()
        val consumer = PaymentCompensationConsumer(
            compensationEventLogService,
            paymentCommandService,
            mock<CompensationEventHandlerRegistry>(),
            objectMapper
        )
        val message = objectMapper.writeValueAsString(
            mapOf(
                "eventType" to "PaymentReversalRequested",
                "eventId" to "reversal-${payment.transactionId}",
                "orderId" to payment.orderId,
                "paymentId" to payment.id,
                "userId" to payment.userId
            )
        )

        consumer.handlePaymentReversal(message)

        verify(paymentCommandService).cancelPaymentInternal(payment.id!!)
        assertThat(compensationStatus("reversal-${payment.transactionId}")).isEqualTo("COMPLETED")
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM point_histories WHERE payment_id = ? AND type = 'REFUND'",
                Long::class.java,
                payment.id
            )
        ).isEqualTo(0L)
    }

    @Test
    fun 결제_금액이_0이라_적립_요청이_나가지_않은_결제의_취소_회수는_바로_완료된다() {
        val payment = savedCancelledPayment(BigDecimal.ZERO, "tx-549-4")

        cancelledHandler().handle(paymentCancelledNode(payment))

        assertThat(compensationStatus("payment-cancelled-${payment.id}")).isEqualTo("COMPLETED")
        assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_histories", Long::class.java)
        ).isEqualTo(0L)
    }

    private fun cancelledHandler() =
        PaymentCancelledCompensationHandler(compensationEventLogService, refundPointsService, objectMapper)

    private fun savedSucceededPayment(amount: BigDecimal, transactionId: String): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = USER_ID,
            amount = amount,
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = transactionId, completedAt = LocalDateTime.now())
        return paymentRepository.save(payment)
    }

    private fun savedCancelledPayment(amount: BigDecimal, transactionId: String): Payment {
        val payment = savedSucceededPayment(amount, transactionId)
        payment.updateStatus(
            PaymentStatus.CANCELLED,
            transactionId = payment.transactionId,
            completedAt = payment.completedAt
        )
        return paymentRepository.save(payment)
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

    private fun earn(payment: Payment, earnAmount: BigDecimal) {
        val consumer = PointEventConsumer(
            pointRepository,
            pointHistoryRepository,
            mock(),
            objectMapper,
            KafkaEventMapper(objectMapper),
            ConcurrentMapCacheManager(),
            pointDomainService
        )
        val message = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to payment.transactionId,
                "userId" to payment.userId,
                "orderId" to payment.orderId,
                "paymentId" to payment.id,
                "earnAmount" to earnAmount,
                "reason" to "결제 완료 적립"
            )
        )
        TransactionTemplate(transactionManager).executeWithoutResult {
            consumer.handlePointEarnRequest(message)
        }
    }

    private fun balanceOf(userId: Long): BigDecimal =
        jdbcTemplate.queryForObject("SELECT balance FROM points WHERE user_id = ?", BigDecimal::class.java, userId)!!

    private fun compensationStatus(eventId: String): String? =
        jdbcTemplate.queryForList("SELECT status FROM compensation_event_logs WHERE event_id = ?", String::class.java, eventId)
            .firstOrNull()

    companion object {
        private const val USER_ID = 549L
        private const val ORDER_ID = 5490L
    }
}
