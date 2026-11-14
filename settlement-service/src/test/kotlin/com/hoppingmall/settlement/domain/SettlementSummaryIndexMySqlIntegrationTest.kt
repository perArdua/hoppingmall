package com.hoppingmall.settlement.domain

import com.hoppingmall.settlement.config.JpaConfig
import com.hoppingmall.settlement.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

@DisplayName("정산 요약 조회 인덱스 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class SettlementSummaryIndexMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Test
    fun 스냅샷_잡이_정산_ID_묶음으로_요약을_찾는_조회는_인덱스를_탄다() {
        val plan = explain("SELECT id FROM settlement_summaries WHERE settlement_id IN (1, 2, 3)")

        assertThat(plan["key"]).isEqualTo(SETTLEMENT_ID_INDEX)
        assertThat(plan["type"]).isNotEqualTo("ALL")
    }

    @Test
    fun 요약_동기화가_정산_ID로_요약을_찾는_조회는_인덱스를_탄다() {
        val plan = explain("SELECT id FROM settlement_summaries WHERE settlement_id = 1")

        assertThat(plan["key"]).isEqualTo(SETTLEMENT_ID_INDEX)
        assertThat(plan["type"]).isNotEqualTo("ALL")
    }

    private fun explain(sql: String): Map<String, Any?> = jdbcTemplate.queryForMap("EXPLAIN $sql")

    companion object {
        private const val SETTLEMENT_ID_INDEX = "idx_settlement_summaries_settlement_id"
    }
}
