package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.PaymentResult
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.service.PointCommandServiceImpl
import com.hoppingmall.payment.point.service.PointDomainService
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("결제 포인트 사용·되돌림 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PaymentPointUseMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val orderSummaryQueryPort = FakeOrderSummaryQueryPort()
    private val paymentService = mock<PaymentService>()

    private lateinit var pointCommandService: PointCommandServiceImpl

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM point_histories")
        jdbcTemplate.update("DELETE FROM points")
        jdbcTemplate.update("DELETE FROM payments")
        orderSummaryQueryPort.put(ORDER_ID, BUYER_ID, ORDER_TOTAL)
        pointCommandService = PointCommandServiceImpl(
            pointRepository,
            pointHistoryRepository,
            PointDomainService(pointRepository, transactionManager)
        )
        givenPg(succeeds = true)
    }

    @Test
    fun 포인트_3천을_쓰면_잔액이_줄고_현금_결제액은_주문_금액에서_포인트를_뺀_값이다() {
        givenBalance(BigDecimal("10000"))

        val result = service().processPayment(request(pointAmount = BigDecimal("3000")), BUYER_ID)

        assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(result.amount).isEqualByComparingTo("27000")
        assertThat(result.pointAmount).isEqualByComparingTo("3000")
        assertThat(balance()).isEqualByComparingTo("7000")
        assertThat(historyAmounts()).containsExactly("USE:-3000.00")
    }

    @Test
    fun 결제가_실패하면_쓴_포인트를_되돌린다() {
        givenBalance(BigDecimal("10000"))
        givenPg(succeeds = false)

        val result = service().processPayment(request(pointAmount = BigDecimal("3000")), BUYER_ID)

        assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(balance()).isEqualByComparingTo("10000")
        assertThat(historyAmounts()).containsExactly("USE:-3000.00", "REFUND:3000.00")
    }

    @Test
    fun 결제를_두_번_취소해도_포인트는_한_번만_되돌린다() {
        givenBalance(BigDecimal("10000"))
        val paid = service().processPayment(request(pointAmount = BigDecimal("3000")), BUYER_ID)

        inTx { service().cancelPaymentInternal(paid.id) }
        inTx { service().cancelPaymentInternal(paid.id) }

        assertThat(balance()).isEqualByComparingTo("10000")
        assertThat(historyAmounts()).containsExactly("USE:-3000.00", "REFUND:3000.00")
    }

    @Test
    fun 부분_환불_뒤_전체_환불과_취소가_와도_되돌린_포인트_합은_쓴_포인트를_넘지_않는다() {
        givenBalance(BigDecimal("10000"))
        val paid = service().processPayment(request(pointAmount = BigDecimal("3000")), BUYER_ID)

        inTx { pointCommandService.refundPoints(BUYER_ID, BigDecimal("1000"), paid.id, ORDER_ID, 1L) }
        inTx { pointCommandService.refundPoints(BUYER_ID, BigDecimal("1000"), paid.id, ORDER_ID, 1L) }
        inTx { pointCommandService.refundPoints(BUYER_ID, BigDecimal("2500"), paid.id, ORDER_ID, 2L) }
        inTx { service().cancelPaymentInternal(paid.id) }

        assertThat(balance()).isEqualByComparingTo("10000")
        assertThat(historyAmounts()).containsExactly("USE:-3000.00", "REFUND:1000.00", "REFUND:2000.00")
    }

    @Test
    fun 잔액보다_많이_쓰면_400_PNT001이고_결제도_차감도_남지_않는다() {
        givenBalance(BigDecimal("2000"))

        val thrown = catchThrowable { service().processPayment(request(pointAmount = BigDecimal("3000")), BUYER_ID) }

        assertThat((thrown as BusinessException).errorCode.code).isEqualTo("PNT001")
        assertThat(balance()).isEqualByComparingTo("2000")
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Int::class.java)).isZero()
        assertThat(historyAmounts()).isEmpty()
    }

    @Test
    fun 결제할_금액보다_많은_포인트는_400_PNT007이다() {
        givenBalance(BigDecimal("50000"))

        val thrown = catchThrowable { service().processPayment(request(pointAmount = BigDecimal("30001")), BUYER_ID) }

        assertThat((thrown as BusinessException).errorCode.code).isEqualTo("PNT007")
        assertThat(balance()).isEqualByComparingTo("50000")
    }

    @Test
    fun 일_포인트_단위가_아니면_400_PNT006이다() {
        givenBalance(BigDecimal("10000"))

        val thrown = catchThrowable { service().processPayment(request(pointAmount = BigDecimal("100.5")), BUYER_ID) }

        assertThat((thrown as BusinessException).errorCode.code).isEqualTo("PNT006")
        assertThat(balance()).isEqualByComparingTo("10000")
    }

    @Test
    fun 전부_포인트로_내면_현금_0원_결제로_PG를_부르지_않고_성공한다() {
        givenBalance(BigDecimal("30000"))

        val result = service().processPayment(request(pointAmount = ORDER_TOTAL), BUYER_ID)

        assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(result.amount).isEqualByComparingTo("0")
        assertThat(balance()).isEqualByComparingTo("0")
        verify(paymentService, never()).processPayment(any())
    }

    @Test
    fun 같은_사용자의_두_결제가_동시에_포인트를_써도_잔액을_넘게_쓰지_않는다() {
        givenBalance(BigDecimal("5000"))
        orderSummaryQueryPort.put(OTHER_ORDER_ID, BUYER_ID, ORDER_TOTAL)
        val start = CountDownLatch(1)

        val futures = listOf(ORDER_ID, OTHER_ORDER_ID).map { orderId ->
            executor.submit(Callable {
                start.await()
                runCatching { service().processPayment(request(orderId = orderId, pointAmount = BigDecimal("4000")), BUYER_ID) }
            })
        }
        start.countDown()
        val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }

        assertThat(outcomes.count { it.isSuccess }).isEqualTo(1)
        assertThat(outcomes.mapNotNull { (it.exceptionOrNull() as? BusinessException)?.errorCode?.code }).containsExactly("PNT001")
        assertThat(balance()).isEqualByComparingTo("1000")
    }

    @Test
    fun 환불_되돌림과_결제_취소가_동시에_와도_쓴_포인트만큼만_되돌린다() {
        givenBalance(BigDecimal("10000"))
        val paid = service().processPayment(request(pointAmount = BigDecimal("3000")), BUYER_ID)
        val start = CountDownLatch(1)

        val futures = listOf<Callable<Result<Any?>>>(
            Callable { start.await(); runCatching { inTx { pointCommandService.refundPoints(BUYER_ID, BigDecimal("3000"), paid.id, ORDER_ID, 7L) } } },
            Callable { start.await(); runCatching { inTx { service().cancelPaymentInternal(paid.id) } } }
        ).map { executor.submit(it) }
        start.countDown()
        val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }

        assertThat(outcomes.map { it.exceptionOrNull() }).containsOnlyNulls()
        assertThat(balance()).isEqualByComparingTo("10000")
        assertThat(restoredSum()).isEqualByComparingTo("3000")
    }

    private fun service(): PaymentCommandServiceImpl {
        val pointEarnRateStrategy = mock<PointEarnRateStrategy>()
        whenever(pointEarnRateStrategy.getEarnRate(any())).thenReturn(BigDecimal("0.01"))
        return PaymentCommandServiceImpl(
            paymentRepository,
            paymentService,
            PaymentEventService(KafkaPaymentEventPublisher(mock<TransactionalEventPublisher>())),
            mock<PaymentNotificationService>(),
            mock(),
            PaymentMetrics(SimpleMeterRegistry()),
            pointEarnRateStrategy,
            orderSummaryQueryPort,
            transactionManager,
            pointCommandService
        )
    }

    private fun givenPg(succeeds: Boolean) {
        whenever(paymentService.processPayment(any())).thenAnswer { invocation ->
            val payment = invocation.arguments[0] as Payment
            if (succeeds) PaymentResult.success(payment, "tx-point-${payment.id}") else PaymentResult.failed(payment, "카드 한도 초과")
        }
    }

    private fun givenBalance(balance: BigDecimal) {
        pointRepository.saveAndFlush(Point(userId = BUYER_ID, balance = balance))
    }

    private fun request(orderId: Long = ORDER_ID, pointAmount: BigDecimal) = PaymentRequest(
        orderId = orderId,
        amount = ORDER_TOTAL,
        method = PaymentMethod.CREDIT_CARD,
        pointAmount = pointAmount
    )

    private fun <T> inTx(action: () -> T): T? = TransactionTemplate(transactionManager).execute { action() }

    private fun balance(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT balance FROM points WHERE user_id = ?", BigDecimal::class.java, BUYER_ID)!!

    private fun historyAmounts(): List<String> =
        jdbcTemplate.query("SELECT type, amount FROM point_histories WHERE user_id = ? ORDER BY id", { rs, _ ->
            "${rs.getString("type")}:${rs.getBigDecimal("amount").toPlainString()}"
        }, BUYER_ID)

    private fun restoredSum(): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM point_histories WHERE user_id = ? AND type = 'REFUND'",
            BigDecimal::class.java,
            BUYER_ID
        )!!

    companion object {
        private const val BUYER_ID = 31L
        private const val ORDER_ID = 7001L
        private const val OTHER_ORDER_ID = 7002L
        private val ORDER_TOTAL = BigDecimal("30000")
    }
}
