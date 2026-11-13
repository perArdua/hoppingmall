package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.payment.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.payment.refund.service.RefundLocalOperationService
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@DisplayName("부분 환불 뒤 결제 취소의 멤버십 되돌리기 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PaymentCancelAfterRefundMembershipRevertMySqlIntegrationTest : MySqlJpaTestSupport() {

    private data class Revert(val reason: String, val amount: BigDecimal)

    private val reverts = CopyOnWriteArrayList<Revert>()

    private val recorder = object : TransactionalEventPublisher {
        override fun publishEvent(
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            eventData: Any,
            topic: String,
            partitionKey: String?
        ) {
            if (topic != KafkaTopics.MEMBERSHIP_UPDATE_REQUEST || eventType != "MembershipRevertRequested") return
            val data = eventData as Map<*, *>
            reverts.add(Revert(data["reason"] as String, data["amount"] as BigDecimal))
        }
    }

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanPaymentTables() {
        listOf("refund_event_log_steps", "refund_event_logs", "payments")
            .forEach { jdbcTemplate.update("DELETE FROM $it") }
        reverts.clear()
    }

    @Test
    fun 부분_환불_뒤_결제가_취소되면_취소는_환불로_되돌리지_않은_금액만_되돌린다() {
        val payment = savedSucceededPayment(BigDecimal("89000"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("30000"))
        cancel(payment)

        assertThat(revertedAmount("REFUND")).isEqualByComparingTo(BigDecimal("30000"))
        assertThat(revertedAmount("CANCEL")).isEqualByComparingTo(BigDecimal("59000"))
        assertThat(revertedAmount()).isEqualByComparingTo(BigDecimal("89000"))
    }

    @Test
    fun 부분_환불이_두_번_끝난_뒤_결제가_취소되면_두_환불을_뺀_금액만_되돌린다() {
        val payment = savedSucceededPayment(BigDecimal("89000"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("20000"))
        refund(payment, refundId = 2L, refundAmount = BigDecimal("30000"))
        cancel(payment)

        assertThat(revertedAmount("CANCEL")).isEqualByComparingTo(BigDecimal("39000"))
        assertThat(revertedAmount()).isEqualByComparingTo(BigDecimal("89000"))
    }

    @Test
    fun 같은_환불_이벤트가_다시_와도_취소가_되돌리는_금액은_한_번만_줄어든다() {
        val payment = savedSucceededPayment(BigDecimal("89000"))

        refund(payment, refundId = 1L, refundAmount = BigDecimal("30000"))
        refund(payment, refundId = 1L, refundAmount = BigDecimal("30000"))
        cancel(payment)

        assertThat(revertedAmount("CANCEL")).isEqualByComparingTo(BigDecimal("59000"))
    }

    @Test
    fun 취소가_결제를_잠근_동안_끝난_부분_환불은_취소가_이미_되돌린_금액을_다시_되돌리지_않는다() {
        val payment = savedSucceededPayment(BigDecimal("89000"))
        val cancelHolding = CountDownLatch(1)
        val releaseCancel = CountDownLatch(1)

        val cancelling = executor.submit {
            TransactionTemplate(transactionManager).executeWithoutResult {
                commandService().cancelPaymentInternal(payment.id!!)
                cancelHolding.countDown()
                releaseCancel.await(20, TimeUnit.SECONDS)
            }
        }
        assertThat(cancelHolding.await(20, TimeUnit.SECONDS)).isTrue()

        val refunding = executor.submit { refund(payment, refundId = 1L, refundAmount = BigDecimal("30000")) }
        awaitBlockedOrDone(refunding)
        releaseCancel.countDown()
        cancelling.get(30, TimeUnit.SECONDS)
        refunding.get(30, TimeUnit.SECONDS)

        assertThat(revertedAmount()).isEqualByComparingTo(BigDecimal("89000"))
    }

    private fun awaitBlockedOrDone(pending: Future<*>) {
        val deadline = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < deadline) {
            if (pending.isDone || lockWaitCount("payments") > 0) return
            Thread.onSpinWait()
        }
        throw AssertionError("환불 처리가 15초 안에 끝나지도, payments 잠금을 기다리지도 않았다")
    }

    private fun revertedAmount(reason: String? = null): BigDecimal =
        reverts.filter { reason == null || it.reason == reason }.fold(BigDecimal.ZERO) { sum, it -> sum.add(it.amount) }

    private fun eventService() = PaymentEventService(KafkaPaymentEventPublisher(recorder))

    private fun commandService() = PaymentCommandServiceImpl(
        paymentRepository,
        mock(),
        eventService(),
        mock(),
        mock(),
        mock(),
        mock(),
        FakeOrderSummaryQueryPort(),
        transactionManager
    )

    private fun cancel(payment: Payment) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            commandService().cancelPaymentInternal(payment.id!!)
        }
    }

    private fun refund(payment: Payment, refundId: Long, refundAmount: BigDecimal) {
        val eventId = "refund-completed-$refundId"
        val event = RefundCompletedEvent(
            eventId = eventId,
            refundId = refundId,
            orderId = payment.orderId,
            paymentId = payment.id!!,
            buyerId = USER_ID,
            refundAmount = refundAmount,
            pointRefundAmount = BigDecimal.ZERO,
            isFullRefund = false,
            couponId = null,
            items = emptyList()
        )
        val service = RefundLocalOperationService(
            paymentRepository,
            mock(),
            mock(),
            refundEventLogRepository,
            mock(),
            eventService(),
            mock()
        )
        TransactionTemplate(transactionManager).executeWithoutResult {
            val eventLog = refundEventLogRepository.findByEventIdWithSteps(eventId)
                ?: refundEventLogRepository.save(
                    RefundEventLog(eventId = eventId, eventType = "REFUND_COMPLETED", refundId = refundId, orderId = payment.orderId)
                )
            service.execute(event, eventLog)
        }
    }

    private fun savedSucceededPayment(amount: BigDecimal): Payment {
        val payment = Payment.create(
            orderId = ORDER_ID,
            userId = USER_ID,
            amount = amount,
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "tx-783", completedAt = LocalDateTime.now())
        return paymentRepository.save(payment)
    }

    companion object {
        private const val USER_ID = 783L
        private const val ORDER_ID = 7830L
    }
}
