package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.config.PaymentMetrics
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("같은 주문의 결제 건수 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PaymentDuplicateOrderMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val orderSummaryQueryPort = FakeOrderSummaryQueryPort()

    @BeforeEach
    fun cleanPayments() {
        jdbcTemplate.update("DELETE FROM payments")
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)
    }

    private fun service(repository: PaymentRepository = paymentRepository): PaymentCommandServiceImpl {
        val paymentService = mock<PaymentService>()
        whenever(paymentService.processPayment(any())).thenAnswer { invocation ->
            val payment = invocation.arguments[0] as Payment
            PaymentResult.success(payment, "tx-duplicate-${payment.id}")
        }
        val pointEarnRateStrategy = mock<PointEarnRateStrategy>()
        whenever(pointEarnRateStrategy.getEarnRate(any())).thenReturn(BigDecimal("0.01"))
        return PaymentCommandServiceImpl(
            repository,
            paymentService,
            mock(),
            mock(),
            mock(),
            PaymentMetrics(SimpleMeterRegistry()),
            pointEarnRateStrategy,
            orderSummaryQueryPort,
            transactionManager
        )
    }

    private fun request() = PaymentRequest(
        orderId = ORDER_ID,
        amount = ORDER_TOTAL,
        method = PaymentMethod.CREDIT_CARD
    )

    private fun paymentRowCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Long::class.java, ORDER_ID)!!

    @Test
    fun 같은_주문의_결제를_차례로_두_번_요청하면_두_번째는_409이고_결제_행은_하나다() {
        val service = service()

        val first = service.processPayment(request(), BUYER_ID)
        val second = runCatching { service.processPayment(request(), BUYER_ID) }

        assertThat(first.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(second.exceptionOrNull())
            .describedAs("두 번째 결제 요청 결과=%s", second)
            .isInstanceOf(PaymentDuplicateException::class.java)
        assertThat(paymentRowCount()).isEqualTo(1L)
    }

    @Test
    fun 같은_주문의_결제_두_건이_동시에_오면_뒤의_결제는_앞의_결제_행을_기다렸다가_409로_끝나고_결제_행은_하나다() {
        val firstInserted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val holdingRepository = HoldingPaymentRepository(paymentRepository, firstInserted, releaseFirst)
        val firstService = service(holdingRepository)
        val secondService = service()

        val first = executor.submit<Result<PaymentResponse>> {
            runCatching { firstService.processPayment(request(), BUYER_ID) }
        }
        assertThat(firstInserted.await(15, TimeUnit.SECONDS)).isTrue()

        val enteredAt = AtomicLong(0L)
        val second = executor.submit<Result<PaymentResponse>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching { secondService.processPayment(request(), BUYER_ID) }
        }
        awaitLockWait(second, enteredAt, "두 번째 결제 요청", "payments")
        releaseFirst.countDown()

        val firstResult = first.get(30, TimeUnit.SECONDS)
        val secondResult = second.get(30, TimeUnit.SECONDS)

        assertThat(firstResult.getOrNull()?.status)
            .describedAs("첫 결제 요청 결과=%s", firstResult)
            .isEqualTo(PaymentStatus.SUCCESS)
        assertThat(secondResult.exceptionOrNull())
            .describedAs("두 번째 결제 요청 결과=%s", secondResult)
            .isInstanceOf(PaymentDuplicateException::class.java)
        assertThat(paymentRowCount()).isEqualTo(1L)
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
        private const val ORDER_ID = 548L
        private const val BUYER_ID = 10L
        private val ORDER_TOTAL = BigDecimal("30000.00")
    }
}
