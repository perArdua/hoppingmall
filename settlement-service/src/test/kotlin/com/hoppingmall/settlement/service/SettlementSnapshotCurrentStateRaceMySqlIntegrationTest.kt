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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
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

@DisplayName("정산 스냅샷과 확정·지급이 겹칠 때 요약의 최신 상태 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(OutputCaptureExtension::class)
@Import(JpaConfig::class, SettlementSnapshotCurrentStateRaceMySqlIntegrationTest.SettlementServices::class)
class SettlementSnapshotCurrentStateRaceMySqlIntegrationTest : MySqlJpaTestSupport() {

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
    fun 요약_표가_빈_상태의_첫_스냅샷이_커밋하기_전에_정산을_확정하면_요약은_확정_상태가_된다() {
        val target = saveSettlement(sellerId = 1L)
        val untouched = saveSettlement(sellerId = 2L)

        val heldSnapshot = hold { settlementSnapshotService.createSnapshot() }
        val confirm = executor.submit<Result<SettlementResponse>> {
            runCatching { settlementCommandService.confirmSettlement(target) }
        }
        awaitDoneOrLockWait(confirm)
        heldSnapshot.release.countDown()

        assertThat(heldSnapshot.result.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(confirm.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(settlementStatus(target)).isEqualTo("CONFIRMED")
        assertThat(summaryStatuses(target)).containsExactly("CONFIRMED")
        assertThat(summaryStatuses(untouched)).containsExactly("CALCULATED")
    }

    @Test
    fun 요약_표가_빈_상태의_첫_스냅샷이_커밋하기_전에_정산을_지급하면_요약은_지급_상태가_된다() {
        val target = saveSettlement(sellerId = 3L, confirmed = true)

        val heldSnapshot = hold { settlementSnapshotService.createSnapshot() }
        val pay = executor.submit<Result<SettlementResponse>> {
            runCatching { settlementCommandService.paySettlement(target) }
        }
        awaitDoneOrLockWait(pay)
        heldSnapshot.release.countDown()

        assertThat(heldSnapshot.result.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(pay.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(settlementStatus(target)).isEqualTo("PAID")
        assertThat(summaryStatuses(target)).containsExactly("PAID")
    }

    @Test
    fun 요약_표가_빈_상태에서_확정이_커밋하기_전에_첫_스냅샷이_돌면_요약은_확정_상태가_된다() {
        val target = saveSettlement(sellerId = 4L)
        val untouched = saveSettlement(sellerId = 5L)

        val heldConfirm = hold { settlementCommandService.confirmSettlement(target) }
        val snapshot = executor.submit<Result<Unit>> {
            runCatching { settlementSnapshotService.createSnapshot() }
        }
        awaitDoneOrLockWait(snapshot)
        heldConfirm.release.countDown()

        assertThat(heldConfirm.result.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(snapshot.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(settlementStatus(target)).isEqualTo("CONFIRMED")
        assertThat(summaryStatuses(target)).containsExactly("CONFIRMED")
        assertThat(summaryStatuses(untouched)).containsExactly("CALCULATED")
    }

    @Test
    fun 확정이_먼저_넣은_요약은_스냅샷_완료_로그의_created에_세지_않는다(output: CapturedOutput) {
        val other = saveSettlement(sellerId = 6L)
        settlementSummaryRepository.save(SettlementSummary.from(settlementRepository.findById(other).get()))
        val target = saveSettlement(sellerId = 7L)

        val heldConfirm = hold { settlementCommandService.confirmSettlement(target) }
        val snapshot = executor.submit<Result<Unit>> {
            runCatching { settlementSnapshotService.createSnapshot() }
        }
        awaitDoneOrLockWait(snapshot)
        heldConfirm.release.countDown()

        assertThat(heldConfirm.result.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(snapshot.get(30, TimeUnit.SECONDS).isSuccess).isTrue()
        assertThat(summaryStatuses(target)).containsExactly("CONFIRMED")
        assertThat(output.out).contains("Settlement snapshot job completed: created=0, updated=2")
    }

    private fun awaitDoneOrLockWait(pending: Future<*>) {
        val deadline = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < deadline) {
            if (pending.isDone) return
            if (lockWaitCount("settlements") + lockWaitCount("settlement_summaries") > 0) return
            Thread.onSpinWait()
        }
        throw AssertionError("15초 안에 끝나지도 잠금 대기에 들어가지도 않았다")
    }

    private fun saveSettlement(sellerId: Long, confirmed: Boolean = false): Long {
        val settlement = Settlement.create(
            sellerId = sellerId,
            periodStart = LocalDate.of(2026, 1, 1),
            periodEnd = LocalDate.of(2026, 1, 31),
            totalSalesAmount = BigDecimal("100000"),
            totalRefundAmount = BigDecimal("5000"),
            commissionRate = BigDecimal("0.05"),
            commissionAmount = BigDecimal("5000.00"),
            settlementAmount = BigDecimal("90000.00")
        )
        if (confirmed) settlement.confirm()
        return settlementRepository.save(settlement).id!!
    }

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
        assertThat(reached.await(15, TimeUnit.SECONDS)).describedAs("먼저 시작한 쪽이 커밋 직전까지 오지 못함").isTrue()
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
