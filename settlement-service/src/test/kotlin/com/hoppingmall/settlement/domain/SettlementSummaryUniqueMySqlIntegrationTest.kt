package com.hoppingmall.settlement.domain

import com.hoppingmall.settlement.config.JpaConfig
import com.hoppingmall.settlement.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

@DisplayName("정산 요약의 정산 ID 유니크 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class SettlementSummaryUniqueMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Test
    fun 정산_ID_열_하나로_된_유니크_제약이_있다() {
        val uniqueIndexes = jdbcTemplate.queryForList(
            """
            SELECT INDEX_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_summaries' AND NON_UNIQUE = 0
            GROUP BY INDEX_NAME
            HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'settlement_id'
            """.trimIndent(),
            String::class.java
        )

        assertThat(uniqueIndexes).containsExactly(SETTLEMENT_ID_UNIQUE)
    }

    @Test
    fun 정산_ID로_요약을_찾는_조회는_유니크_인덱스를_탄다() {
        insertSummary(1L)

        val plan = jdbcTemplate.queryForMap("EXPLAIN SELECT id FROM settlement_summaries WHERE settlement_id = 1")

        assertThat(plan["key"]).isEqualTo(SETTLEMENT_ID_UNIQUE)
    }

    @Test
    fun 정산_ID_일반_인덱스가_유니크와_겹쳐_남아_있지_않다() {
        val nonUnique = jdbcTemplate.queryForList(
            """
            SELECT INDEX_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_summaries' AND NON_UNIQUE = 1
            AND COLUMN_NAME = 'settlement_id'
            """.trimIndent(),
            String::class.java
        )

        assertThat(nonUnique).isEmpty()
    }

    private fun insertSummary(settlementId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO settlement_summaries (settlement_id, seller_id, period_start, period_end, total_sales_amount,
              total_refund_amount, commission_rate, commission_amount, settlement_amount, status, created_at, updated_at)
            VALUES (?, 1, '2026-01-01', '2026-01-02', 0, 0, 0, 0, 0, 'CALCULATED', NOW(), NOW())
            """.trimIndent(),
            settlementId
        )
    }

    companion object {
        private const val SETTLEMENT_ID_UNIQUE = "uk_settlement_summaries_settlement_id"
    }
}
