package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.config.JpaConfig
import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundQueryPort
import com.hoppingmall.settlement.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("정산 요약 첫 삽입 경합 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class, SettlementSummaryFirstInsertRaceMySqlIntegrationTest.SettlementServices::class)
class SettlementSummaryFirstInsertRaceMySqlIntegrationTest : MySqlJpaTestSupport() {

    @TestConfiguration
    @ComponentScan(basePackages = ["com.hoppingmall.settlement.service"])
    class SettlementServices

    @Autowired
    private lateinit var settlementCommandService: SettlementCommandService

    @Autowired
    private lateinit var settlementSnapshotService: SettlementSnapshotService

    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Autowired
    private lateinit var settlementSummaryRepository: SettlementSummaryRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var refundQueryPort: RefundQueryPort

    @Test
    fun 스냅샷_잡이_요약을_먼저_넣고_커밋하기_전에_정산을_확정하면_확정은_기다린_뒤_요약을_갱신하고_롤백되지_않는다() {
        seedSummaryOfOtherSettlement()
        val target = saveSettlement(sellerId = 2L)

        val heldSnapshot = hold { settlementSnapshotService.createSnapshot() }
        val enteredAt = AtomicLong(0L)
        val confirm = executor.submit<Result<SettlementResponse>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching { settlementCommandService.confirmSettlement(target) }
        }
        awaitLockWait(confirm, enteredAt, "정산 확정", "settlement_summaries")
        heldSnapshot.release.countDown()

        val snapshotResult = heldSnapshot.result.get(30, TimeUnit.SECONDS)
        val confirmResult = confirm.get(30, TimeUnit.SECONDS)

        assertThat(snapshotResult.isSuccess).describedAs("스냅샷=%s", snapshotResult).isTrue()
        assertThat(confirmResult.isSuccess).describedAs("확정=%s", confirmResult).isTrue()
        assertThat(settlementStatus(target)).isEqualTo("CONFIRMED")
        assertThat(summaryStatuses(target)).containsExactly("CONFIRMED")
    }

    @Test
    fun 스냅샷_잡이_요약을_먼저_넣고_커밋하기_전에_정산을_지급하면_지급은_기다린_뒤_요약을_갱신하고_롤백되지_않는다() {
        seedSummaryOfOtherSettlement()
        val target = saveSettlement(sellerId = 3L, confirmed = true)

        val heldSnapshot = hold { settlementSnapshotService.createSnapshot() }
        val enteredAt = AtomicLong(0L)
        val pay = executor.submit<Result<SettlementResponse>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching { settlementCommandService.paySettlement(target) }
        }
        awaitLockWait(pay, enteredAt, "정산 지급", "settlement_summaries")
        heldSnapshot.release.countDown()

        val snapshotResult = heldSnapshot.result.get(30, TimeUnit.SECONDS)
        val payResult = pay.get(30, TimeUnit.SECONDS)

        assertThat(snapshotResult.isSuccess).describedAs("스냅샷=%s", snapshotResult).isTrue()
        assertThat(payResult.isSuccess).describedAs("지급=%s", payResult).isTrue()
        assertThat(settlementStatus(target)).isEqualTo("PAID")
        assertThat(summaryStatuses(target)).containsExactly("PAID")
    }

    @Test
    fun 정산_확정이_요약을_먼저_넣고_커밋하기_전에_스냅샷_잡이_돌면_잡은_기다린_뒤_나머지_요약까지_만들고_롤백되지_않는다() {
        seedSummaryOfOtherSettlement()
        val target = saveSettlement(sellerId = 4L)
        val untouched = saveSettlement(sellerId = 5L)

        val heldConfirm = hold { settlementCommandService.confirmSettlement(target) }
        val enteredAt = AtomicLong(0L)
        val snapshot = executor.submit<Result<Unit>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching { settlementSnapshotService.createSnapshot() }
        }
        awaitLockWait(snapshot, enteredAt, "스냅샷 잡", "settlement_summaries")
        heldConfirm.release.countDown()

        val confirmResult = heldConfirm.result.get(30, TimeUnit.SECONDS)
        val snapshotResult = snapshot.get(30, TimeUnit.SECONDS)

        assertThat(confirmResult.isSuccess).describedAs("확정=%s", confirmResult).isTrue()
        assertThat(snapshotResult.isSuccess).describedAs("스냅샷=%s", snapshotResult).isTrue()
        assertThat(settlementStatus(target)).isEqualTo("CONFIRMED")
        assertThat(summaryStatuses(target)).containsExactly("CONFIRMED")
        assertThat(summaryStatuses(untouched)).containsExactly("CALCULATED")
    }

    private fun seedSummaryOfOtherSettlement() {
        val other = settlementRepository.save(newSettlement(sellerId = 1L))
        settlementSummaryRepository.save(SettlementSummary.from(other))
    }

    private fun saveSettlement(sellerId: Long, confirmed: Boolean = false): Long {
        val settlement = newSettlement(sellerId)
        if (confirmed) settlement.confirm()
        return settlementRepository.save(settlement).id!!
    }

    private fun newSettlement(sellerId: Long): Settlement =
        Settlement.create(
            sellerId = sellerId,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("100000"),
            totalRefundAmount = BigDecimal("5000"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("5000.00"),
            settlementAmount = BigDecimal("90000.00")
        )

    private fun <T> hold(action: () -> T): Held<T> {
        val transactionTemplate = TransactionTemplate(transactionManager)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val result = executor.submit<Result<T>> {
            runCatching {
                transactionTemplate.execute {
                    val value = action()
                    reached.countDown()
                    release.await(30, TimeUnit.SECONDS)
                    value
                } as T
            }
        }
        assertThat(reached.await(15, TimeUnit.SECONDS)).describedAs("먼저 넣는 쪽이 커밋 직전까지 오지 못함").isTrue()
        return Held(result, release)
    }

    private fun settlementStatus(settlementId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM settlements WHERE id = ?", String::class.java, settlementId)!!

    private fun summaryStatuses(settlementId: Long): List<String> =
        jdbcTemplate.queryForList("SELECT status FROM settlement_summaries WHERE settlement_id = ?", String::class.java, settlementId)

    private class Held<T>(
        val result: Future<Result<T>>,
        val release: CountDownLatch
    )
}
