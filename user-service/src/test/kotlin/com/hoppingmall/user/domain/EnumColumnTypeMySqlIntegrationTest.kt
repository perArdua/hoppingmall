package com.hoppingmall.user.domain

import com.hoppingmall.user.config.JpaConfig
import com.hoppingmall.user.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

@DisplayName("user 스키마의 enum 열 타입 MySQL 통합 테스트")
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
                ('memberships', 'grade'),
                ('membership_event_logs', 'type'),
                ('sellers', 'approval_status'),
                ('users', 'role')
            )
            """.trimIndent()
        ).associate { it.values.first().toString() to it.values.last().toString() }

        assertThat(columnTypes).containsOnly(
            entry("memberships.grade", "varchar"),
            entry("membership_event_logs.type", "varchar"),
            entry("sellers.approval_status", "varchar"),
            entry("users.role", "varchar")
        )
    }
}
