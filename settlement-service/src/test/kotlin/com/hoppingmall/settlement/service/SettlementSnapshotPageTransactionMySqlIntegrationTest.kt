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
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

@DisplayName("정산 스냅샷 잡의 페이지별 트랜잭션 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class, SettlementSnapshotPageTransactionMySqlIntegrationTest.SettlementServices::class)
class SettlementSnapshotPageTransactionMySqlIntegrationTest : MySqlJpaTestSupport() {

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
    private lateinit var dataSource: DataSource

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var refundQueryPort: RefundQueryPort

    @Test
    fun 스냅샷_잡이_뒤_페이지에서_기다리는_동안_앞_페이지에서_요약을_넣은_정산의_확정은_기다리지_않는다() {
        val settlements = (1L..PAGE_SIZE + 1L).map { saveSettlement(sellerId = it) }
        settlementSummaryRepository.save(SettlementSummary.from(settlements.first()))
        val firstPageTarget = settlements[1].id!!
        val lastPageSettlement = settlements.last()

        dataSource.connection.use { blocker ->
            blocker.autoCommit = false
            insertSummaryWithoutCommit(blocker, lastPageSettlement)
            val enteredAt = AtomicLong(0L)
            val snapshot = executor.submit<Result<Unit>> {
                enteredAt.set(System.currentTimeMillis())
                runCatching { settlementSnapshotService.createSnapshot() }
            }
            val confirmResult = try {
                awaitLockWait(snapshot, enteredAt, "스냅샷 잡", "settlement_summaries")
                val confirm = executor.submit<Result<SettlementResponse>> {
                    runCatching { settlementCommandService.confirmSettlement(firstPageTarget) }
                }
                val result = runCatching { confirm.get(15, TimeUnit.SECONDS) }
                assertThat(snapshot.isDone).describedAs("스냅샷 잡이 뒤 페이지에서 기다리지 않고 끝남").isFalse()
                result
            } finally {
                blocker.rollback()
            }

            val snapshotResult = snapshot.get(30, TimeUnit.SECONDS)

            assertThat(confirmResult.getOrNull()?.isSuccess).describedAs("확정=%s", confirmResult).isTrue()
            assertThat(snapshotResult.isSuccess).describedAs("스냅샷=%s", snapshotResult).isTrue()
        }
        assertThat(summaryStatuses(firstPageTarget)).containsExactly("CONFIRMED")
        assertThat(summaryStatuses(lastPageSettlement.id!!)).containsExactly("CALCULATED")
        assertThat(settlementSummaryRepository.count()).isEqualTo(PAGE_SIZE + 1L)
    }

    @Test
    fun 잡이_뒤_페이지에서_실패해도_앞_페이지의_요약은_남고_다시_돌리면_남은_요약만_더_넣는다() {
        val settlements = (1L..PAGE_SIZE + 1L).map { saveSettlement(sellerId = it) }
        val lastPageSettlement = settlements.last()
        jdbcTemplate.update("UPDATE settlements SET status = 'UNREADABLE' WHERE id = ?", lastPageSettlement.id)

        val failedRun = runCatching { settlementSnapshotService.createSnapshot() }
        val summariesAfterFailure = settlementSummaryRepository.count()
        jdbcTemplate.update("UPDATE settlements SET status = 'CALCULATED' WHERE id = ?", lastPageSettlement.id)
        settlementSnapshotService.createSnapshot()

        assertThat(failedRun.isFailure).isTrue()
        assertThat(summariesAfterFailure).isEqualTo(PAGE_SIZE.toLong())
        assertThat(settlementSummaryRepository.count()).isEqualTo(PAGE_SIZE + 1L)
        assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT settlement_id) FROM settlement_summaries", Long::class.java)
        ).isEqualTo(PAGE_SIZE + 1L)
        assertThat(summaryStatuses(lastPageSettlement.id!!)).containsExactly("CALCULATED")
    }

    private fun insertSummaryWithoutCommit(connection: Connection, settlement: Settlement) {
        connection.prepareStatement(
            """
            INSERT INTO settlement_summaries (settlement_id, seller_id, period_start, period_end, total_sales_amount,
              total_refund_amount, commission_rate, commission_amount, settlement_amount, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, settlement.id!!)
            statement.setLong(2, settlement.sellerId)
            statement.setObject(3, settlement.periodStart)
            statement.setObject(4, settlement.periodEnd)
            statement.setBigDecimal(5, settlement.totalSalesAmount)
            statement.setBigDecimal(6, settlement.totalRefundAmount)
            statement.setBigDecimal(7, settlement.commissionRate)
            statement.setBigDecimal(8, settlement.commissionAmount)
            statement.setBigDecimal(9, settlement.settlementAmount)
            statement.setString(10, settlement.status.name)
            statement.executeUpdate()
        }
    }

    private fun saveSettlement(sellerId: Long): Settlement =
        settlementRepository.save(
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
        )

    private fun summaryStatuses(settlementId: Long): List<String> =
        jdbcTemplate.queryForList("SELECT status FROM settlement_summaries WHERE settlement_id = ?", String::class.java, settlementId)

    companion object {
        private const val PAGE_SIZE = 200
    }
}
