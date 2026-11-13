package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.config.JpaConfig
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.exception.SettlementAlreadyExistsException
import com.hoppingmall.settlement.port.OrderItemInfo
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundQueryPort
import com.hoppingmall.settlement.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("정산 동시 생성 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class, SettlementConcurrentCreationMySqlIntegrationTest.SettlementServices::class)
class SettlementConcurrentCreationMySqlIntegrationTest : MySqlJpaTestSupport() {

    @TestConfiguration
    @ComponentScan(basePackages = ["com.hoppingmall.settlement.service"])
    class SettlementServices

    @Autowired
    private lateinit var settlementCommandService: SettlementCommandService

    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var refundQueryPort: RefundQueryPort

    @BeforeEach
    fun stubPorts() {
        whenever(orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenAnswer { invocation ->
                listOf(
                    OrderItemInfo(
                        id = invocation.getArgument<Long>(0) * 1000 + invocation.getArgument<LocalDateTime>(1).dayOfYear,
                        orderId = 100L,
                        sellerId = 1L,
                        productId = 1L,
                        productName = "상품A",
                        productPrice = BigDecimal("50000"),
                        quantity = 2,
                        totalPrice = BigDecimal("100000")
                    )
                )
            }
        whenever(refundQueryPort.findCompletedBySellerAndPeriod(any(), any<LocalDateTime>(), any<LocalDateTime>()))
            .thenReturn(emptyList())
    }

    @Test
    fun 같은_판매자의_겹치는_정산이_동시에_오면_뒤의_요청은_앞의_커밋을_기다린_뒤_거절된다() {
        val sellerId = 11L
        val held = holdCreation(sellerId, date(1, 1), date(1, 31))

        val enteredAt = AtomicLong(0L)
        val second = executor.submit<Result<SettlementResponse>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching { create(sellerId, date(1, 15), date(2, 15)) }
        }
        awaitLockWait(second, enteredAt, "두 번째 정산 생성", "settlement_seller_locks")
        held.release.countDown()

        val firstResult = held.result.get(30, TimeUnit.SECONDS)
        val secondResult = second.get(30, TimeUnit.SECONDS)

        assertThat(firstResult.isSuccess).describedAs("첫째=%s", firstResult).isTrue()
        assertThat(secondResult.exceptionOrNull()).isInstanceOf(SettlementAlreadyExistsException::class.java)
        assertThat(periodsOf(sellerId)).containsExactly(date(1, 1) to date(1, 31))
    }

    @Test
    fun 같은_판매자의_겹치지_않는_정산은_앞의_커밋을_기다린_뒤_둘_다_만들어진다() {
        val sellerId = 21L
        val held = holdCreation(sellerId, date(1, 1), date(1, 31))

        val enteredAt = AtomicLong(0L)
        val second = executor.submit<Result<SettlementResponse>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching { create(sellerId, date(3, 1), date(3, 31)) }
        }
        awaitLockWait(second, enteredAt, "두 번째 정산 생성", "settlement_seller_locks")
        held.release.countDown()

        val firstResult = held.result.get(30, TimeUnit.SECONDS)
        val secondResult = second.get(30, TimeUnit.SECONDS)

        assertThat(firstResult.isSuccess).describedAs("첫째=%s", firstResult).isTrue()
        assertThat(secondResult.isSuccess).describedAs("둘째=%s", secondResult).isTrue()
        assertThat(periodsOf(sellerId)).containsExactlyInAnyOrder(
            date(1, 1) to date(1, 31),
            date(3, 1) to date(3, 31)
        )
    }

    @Test
    fun 다른_판매자의_정산은_서로_기다리지_않는다() {
        val heldSellerId = 31L
        val otherSellerId = 32L
        val held = holdCreation(heldSellerId, date(1, 1), date(1, 31))

        val other = executor.submit<Result<SettlementResponse>> {
            runCatching { create(otherSellerId, date(1, 1), date(1, 31)) }
        }
        val otherResult = other.get(15, TimeUnit.SECONDS)
        held.release.countDown()
        val heldResult = held.result.get(30, TimeUnit.SECONDS)

        assertThat(otherResult.isSuccess).describedAs("다른 판매자=%s", otherResult).isTrue()
        assertThat(heldResult.isSuccess).describedAs("잡고 있던 판매자=%s", heldResult).isTrue()
        assertThat(periodsOf(otherSellerId)).containsExactly(date(1, 1) to date(1, 31))
        assertThat(periodsOf(heldSellerId)).containsExactly(date(1, 1) to date(1, 31))
    }

    private fun holdCreation(sellerId: Long, start: LocalDate, end: LocalDate): HeldCreation {
        val transactionTemplate = TransactionTemplate(transactionManager)
        val created = CountDownLatch(1)
        val release = CountDownLatch(1)
        val result = executor.submit<Result<SettlementResponse>> {
            runCatching {
                transactionTemplate.execute {
                    val response = create(sellerId, start, end)
                    created.countDown()
                    release.await(30, TimeUnit.SECONDS)
                    response
                }!!
            }
        }
        assertThat(created.await(15, TimeUnit.SECONDS)).describedAs("첫 정산 생성이 커밋 직전까지 오지 못함").isTrue()
        return HeldCreation(result, release)
    }

    private fun create(sellerId: Long, start: LocalDate, end: LocalDate): SettlementResponse =
        settlementCommandService.createSettlement(
            CreateSettlementRequest(
                sellerId = sellerId,
                periodStart = start,
                periodEnd = end,
                commissionRate = BigDecimal("0.05")
            )
        )

    private fun periodsOf(sellerId: Long): List<Pair<LocalDate, LocalDate>> =
        settlementRepository.findAll()
            .filter { it.sellerId == sellerId }
            .map { it.periodStart to it.periodEnd }

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private class HeldCreation(
        val result: Future<Result<SettlementResponse>>,
        val release: CountDownLatch
    )
}
