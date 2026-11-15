package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.internal.InternalPaymentController
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.PaymentResult
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentDuplicateException
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("결제 실패 뒤 같은 주문의 다시 결제 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PaymentRetryAfterFailureMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val orderSummaryQueryPort = FakeOrderSummaryQueryPort()
    private val transactionalEventPublisher = mock<TransactionalEventPublisher>()
    private val paymentNotificationService = mock<PaymentNotificationService>()

    @BeforeEach
    fun cleanPayments() {
        jdbcTemplate.update("DELETE FROM payments")
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)
    }

    private fun service(succeeds: Boolean, repository: PaymentRepository = paymentRepository): PaymentCommandServiceImpl {
        val paymentService = mock<PaymentService>()
        whenever(paymentService.processPayment(any())).thenAnswer { invocation ->
            val payment = invocation.arguments[0] as Payment
            if (succeeds) PaymentResult.success(payment, "tx-retry-${payment.id}") else PaymentResult.failed(payment, "카드 한도 초과")
        }
        val pointEarnRateStrategy = mock<PointEarnRateStrategy>()
        whenever(pointEarnRateStrategy.getEarnRate(any())).thenReturn(BigDecimal("0.01"))
        return PaymentCommandServiceImpl(
            repository,
            paymentService,
            PaymentEventService(KafkaPaymentEventPublisher(transactionalEventPublisher)),
            paymentNotificationService,
            mock(),
            PaymentMetrics(SimpleMeterRegistry()),
            pointEarnRateStrategy,
            orderSummaryQueryPort,
            transactionManager
        )
    }

    private fun request() = PaymentRequest(orderId = ORDER_ID, amount = ORDER_TOTAL, method = PaymentMethod.CREDIT_CARD)

    private fun paymentStatuses(): List<String> =
        jdbcTemplate.queryForList("SELECT status FROM payments WHERE order_id = ? ORDER BY id", String::class.java, ORDER_ID)

    @Test
    fun 결제가_실패한_주문은_다시_결제할_수_있고_실패한_결제는_이력으로_남는다() {
        val failed = service(succeeds = false).processPayment(request(), BUYER_ID)
        val retried = runCatching { service(succeeds = true).processPayment(request(), BUYER_ID) }

        assertThat(failed.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(retried.getOrNull()?.status)
            .describedAs("다시 결제 결과=%s", retried)
            .isEqualTo(PaymentStatus.SUCCESS)
        assertThat(paymentStatuses()).containsExactly("FAILED", "SUCCESS")
    }

    @Test
    fun 결제가_실패해도_주문_취소_보상_이벤트를_내지_않고_실패_알림만_낸다() {
        service(succeeds = false).processPayment(request(), BUYER_ID)

        verify(transactionalEventPublisher, never())
            .publishEvent(any(), any(), any(), any(), eq(KafkaTopics.PAYMENT_COMPENSATION), anyOrNull())
        verify(paymentNotificationService).publishPaymentFailedNotification(any())
    }

    @Test
    fun 실패_뒤_다시_결제가_성공하면_주문의_결제_조회는_새_결제를_주고_목록은_실패_이력까지_최신순으로_준다() {
        service(succeeds = false).processPayment(request(), BUYER_ID)
        val retried = runCatching { service(succeeds = true).processPayment(request(), BUYER_ID) }
        val successId = retried.getOrNull()?.id

        val internal = InternalPaymentController(paymentRepository, service(succeeds = true)).getPaymentByOrderId(ORDER_ID)
        val history = PaymentQueryServiceImpl(paymentRepository).getPaymentsByOrderId(ORDER_ID, BUYER_ID)

        assertThat(successId).describedAs("다시 결제 결과=%s", retried).isNotNull()
        assertThat(internal.body?.id).isEqualTo(successId)
        assertThat(internal.body?.status).isEqualTo("SUCCESS")
        assertThat(service(succeeds = true).findPaymentIdByOrderId(ORDER_ID)).isEqualTo(successId)
        assertThat(history.map { it.status }).containsExactly(PaymentStatus.SUCCESS, PaymentStatus.FAILED)
    }

    @Test
    fun 실패한_결제만_있는_주문의_결제_취소_요청은_404이고_실패한_결제는_그대로다() {
        val failed = service(succeeds = false).processPayment(request(), BUYER_ID)
        val controller = InternalPaymentController(paymentRepository, service(succeeds = true))

        val lookup = controller.getPaymentByOrderId(ORDER_ID)
        val cancel = runCatching {
            TransactionTemplate(transactionManager).execute { controller.cancelPaymentByOrderId(ORDER_ID) }
        }

        assertThat(lookup.body?.id).isEqualTo(failed.id)
        assertThat(lookup.body?.status).isEqualTo("FAILED")
        assertThat(cancel.getOrNull()?.statusCode)
            .describedAs("결제 취소 요청 결과=%s", cancel)
            .isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(paymentStatuses()).containsExactly("FAILED")
    }

    @Test
    fun 실패_뒤_성공한_주문을_또_결제하면_409이고_결제_행은_늘지_않는다() {
        service(succeeds = false).processPayment(request(), BUYER_ID)
        val retried = runCatching { service(succeeds = true).processPayment(request(), BUYER_ID) }
        val third = runCatching { service(succeeds = true).processPayment(request(), BUYER_ID) }

        assertThat(retried.getOrNull()?.status).describedAs("다시 결제 결과=%s", retried).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(third.exceptionOrNull())
            .describedAs("세 번째 결제 결과=%s", third)
            .isInstanceOf(PaymentDuplicateException::class.java)
        assertThat(paymentStatuses()).containsExactly("FAILED", "SUCCESS")
    }

    @Test
    fun 실패_뒤_다시_결제_두_건이_동시에_오면_뒤의_결제는_앞의_결제_행을_기다렸다가_409로_끝나고_성공은_하나다() {
        service(succeeds = false).processPayment(request(), BUYER_ID)
        val firstInserted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstService = service(succeeds = true, repository = HoldingPaymentRepository(paymentRepository, firstInserted, releaseFirst))
        val secondService = service(succeeds = true)

        val first = executor.submit<Result<PaymentResponse>> {
            runCatching { firstService.processPayment(request(), BUYER_ID) }
        }
        assertThat(firstInserted.await(15, TimeUnit.SECONDS))
            .describedAs("첫 다시 결제가 결제 행을 넣지 못함: %s", if (first.isDone) first.get() else "running")
            .isTrue()

        val enteredAt = AtomicLong(0L)
        val second = executor.submit<Result<PaymentResponse>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching { secondService.processPayment(request(), BUYER_ID) }
        }
        awaitLockWait(second, enteredAt, "두 번째 다시 결제", "payments")
        releaseFirst.countDown()

        val firstResult = first.get(30, TimeUnit.SECONDS)
        val secondResult = second.get(30, TimeUnit.SECONDS)

        assertThat(firstResult.getOrNull()?.status)
            .describedAs("첫 다시 결제 결과=%s", firstResult)
            .isEqualTo(PaymentStatus.SUCCESS)
        assertThat(secondResult.exceptionOrNull())
            .describedAs("두 번째 다시 결제 결과=%s", secondResult)
            .isInstanceOf(PaymentDuplicateException::class.java)
        assertThat(paymentStatuses()).containsExactly("FAILED", "SUCCESS")
    }

    private class HoldingPaymentRepository(
        private val delegate: PaymentRepository,
        private val inserted: CountDownLatch,
        private val release: CountDownLatch
    ) : PaymentRepository by delegate {
        override fun <S : Payment> save(entity: S): S {
            val saved = delegate.save(entity)
            if (saved.status == PaymentStatus.PENDING) {
                inserted.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
            return saved
        }
    }

    companion object {
        private const val ORDER_ID = 985L
        private const val BUYER_ID = 10L
        private val ORDER_TOTAL = BigDecimal("30000.00")
    }
}
