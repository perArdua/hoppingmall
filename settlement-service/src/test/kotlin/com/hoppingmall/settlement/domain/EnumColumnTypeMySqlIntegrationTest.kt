package com.hoppingmall.settlement.domain

import com.hoppingmall.settlement.config.JpaConfig
import com.hoppingmall.settlement.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

@DisplayName("settlement 스키마의 enum 열 타입 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class EnumColumnTypeMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Test
    fun 스키마를_만든_뒤_MySQL_ENUM_타입_열이_하나도_없다() {
        val enumColumns = jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'enum'
            ORDER BY TABLE_NAME, COLUMN_NAME
            """.trimIndent(),
            String::class.java
        )

        assertThat(enumColumns).isEmpty()
    }

    @Test
    fun enum_필드의_열은_VARCHAR로_만들어진다() {
        val columnTypes = jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME), DATA_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND (TABLE_NAME, COLUMN_NAME) IN (
                ('settlements', 'status'),
                ('settlement_summaries', 'status')
            )
            """.trimIndent()
        ).associate { it.values.first().toString() to it.values.last().toString() }

        assertThat(columnTypes).containsOnly(
            entry("settlements.status", "varchar"),
            entry("settlement_summaries.status", "varchar")
        )
    }

    @Test
    fun 스키마의_enum_열에_CHECK_제약이_하나도_없다() {
        val checkConstraints = jdbcTemplate.queryForList(
            """
            SELECT CONCAT(tc.TABLE_NAME, '.', tc.CONSTRAINT_NAME, ' ', cc.CHECK_CLAUSE)
            FROM information_schema.TABLE_CONSTRAINTS tc
            JOIN information_schema.CHECK_CONSTRAINTS cc
            ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
            WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.CONSTRAINT_TYPE = 'CHECK'
            ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME
            """.trimIndent(),
            String::class.java
        )

        assertThat(checkConstraints).isEmpty()
    }

    @Test
    fun enum에_없는_새_값도_상태_열에_저장된다() {
        jdbcTemplate.update(
            """
            INSERT INTO settlements (created_at, seller_id, period_start, period_end, total_sales_amount, total_refund_amount,
                commission_rate, commission_amount, settlement_amount, status)
            VALUES (NOW(6), 1, '2026-11-01', '2026-11-30', 0, 0, 0, 0, 0, ?)
            """.trimIndent(),
            VALUE_ADDED_LATER
        )

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM settlements WHERE seller_id = 1", String::class.java))
            .isEqualTo(VALUE_ADDED_LATER)
    }

    companion object {
        private const val VALUE_ADDED_LATER = "VALUE_ADDED_LATER"
    }
}
