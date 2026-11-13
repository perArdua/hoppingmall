package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
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

@DisplayName("결제 ID 없는 역보상 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(CompensationEventLogService::class)
class PaymentReversalWithoutPaymentIdMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanPaymentTables() {
        listOf("compensation_event_logs", "payments").forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 결제_ID가_빠진_역보상은_주문_ID로_결제를_찾아_취소하고_완료로_기록한다() {
        val payment = savedSucceededPayment()

        consumer().handlePaymentReversal(reversalMessage(eventId = "reversal-806-1", paymentId = null))

        assertThat(paymentStatus(payment)).isEqualTo("CANCELLED")
        assertThat(compensationLog("reversal-806-1")).isEqualTo("COMPLETED" to payment.id)
    }

    @Test
    fun 결제_ID가_0으로_온_역보상은_주문_ID로_결제를_찾아_취소하고_완료로_기록한다() {
        val payment = savedSucceededPayment()

        consumer().handlePaymentReversal(reversalMessage(eventId = "reversal-806-2", paymentId = 0L))

        assertThat(paymentStatus(payment)).isEqualTo("CANCELLED")
        assertThat(compensationLog("reversal-806-2")).isEqualTo("COMPLETED" to payment.id)
    }

    @Test
    fun 결제_ID가_빠졌고_그_주문의_결제도_없으면_예외_없이_넘어간다() {
        val result = runCatching {
            consumer().handlePaymentReversal(reversalMessage(eventId = "reversal-806-3", paymentId = null))
        }

        assertThat(result.exceptionOrNull()).isNull()
    }

    private fun consumer(): PaymentCompensationConsumer {
        val target = PaymentCommandServiceImpl(
            paymentRepository,
            mock(),
            PaymentEventService(KafkaPaymentEventPublisher(mock())),
            mock(),
            mock(),
            mock(),
            mock(),
            FakeOrderSummaryQueryPort(),
            transactionManager
        )
        val transactionTemplate = TransactionTemplate(transactionManager)
        val transactional = object : PaymentCommandService by target {
            override fun cancelPaymentInternal(paymentId: Long) =
                transactionTemplate.execute { target.cancelPaymentInternal(paymentId) }!!
        }
        return PaymentCompensationConsumer(
            compensationEventLogService,
            transactional,
            mock<CompensationEventHandlerRegistry>(),
            objectMapper
        )
    }

    private fun reversalMessage(eventId: String, paymentId: Long?): String {
        val body = mutableMapOf<String, Any>(
            "eventType" to "PaymentReversalRequested",
            "eventId" to eventId,
            "orderId" to ORDER_ID,
            "userId" to USER_ID
        )
        paymentId?.let { body["paymentId"] = it }
        return objectMapper.writeValueAsString(body)
    }

    private fun savedSucceededPayment(): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = USER_ID,
            amount = BigDecimal("15000"),
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "tx-806", completedAt = LocalDateTime.now())
        return paymentRepository.save(payment)
    }

    private fun paymentStatus(payment: Payment): String =
        jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?", String::class.java, payment.id)!!

    private fun compensationLog(eventId: String): Pair<String, Long>? =
        jdbcTemplate.query(
            "SELECT status, payment_id FROM compensation_event_logs WHERE event_id = ?",
            { rs, _ -> rs.getString("status") to rs.getLong("payment_id") },
            eventId
        ).firstOrNull()

    companion object {
        private const val USER_ID = 806L
        private const val ORDER_ID = 8060L
    }
}
