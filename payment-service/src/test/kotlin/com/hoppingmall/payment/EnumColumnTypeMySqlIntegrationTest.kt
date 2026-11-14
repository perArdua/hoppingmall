package com.hoppingmall.payment

import com.hoppingmall.payment.config.JpaConfig
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

@DisplayName("결제 DB 스키마의 enum 열 타입 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class EnumColumnTypeMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Test
    fun 스키마를_만들면_MySQL_ENUM_타입_열이_하나도_없다() {
        val enumColumns = jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND DATA_TYPE = 'enum'
            ORDER BY TABLE_NAME, COLUMN_NAME
            """.trimIndent(),
            String::class.java
        )

        assertThat(enumColumns).isEmpty()
    }

    @Test
    fun 결제_서비스의_enum_필드_열은_길이_32의_VARCHAR로_만들어진다() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' ', IS_NULLABLE)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME IN ('payments', 'coupons', 'user_coupons', 'point_histories', 'compensation_event_logs')
            AND COLUMN_NAME IN ('method', 'status', 'discount_type', 'type')
            """.trimIndent(),
            String::class.java
        )

        assertThat(columns).containsExactlyInAnyOrder(
            "payments.method varchar(32) NO",
            "payments.status varchar(32) NO",
            "coupons.discount_type varchar(32) NO",
            "coupons.status varchar(32) NO",
            "user_coupons.status varchar(32) NO",
            "point_histories.type varchar(32) NO",
            "compensation_event_logs.status varchar(32) NO"
        )
    }

    @Test
    fun 스키마에_공유_모듈_표의_enum_필드_열도_만들어진다() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            """.trimIndent(),
            String::class.java
        )

        assertThat(columns).contains("dlq_messages.status", "outbox_events.status")
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
    fun enum에_없는_새_값도_포인트_이력_종류_열에_저장된다() {
        try {
            jdbcTemplate.update(
                "INSERT INTO point_histories (created_at, user_id, amount, type, event_id) VALUES (NOW(6), 1, 0, ?, ?)",
                VALUE_ADDED_LATER,
                EVENT_ID
            )

            assertThat(jdbcTemplate.queryForObject("SELECT type FROM point_histories WHERE event_id = ?", String::class.java, EVENT_ID))
                .isEqualTo(VALUE_ADDED_LATER)
        } finally {
            jdbcTemplate.update("DELETE FROM point_histories WHERE event_id = ?", EVENT_ID)
        }
    }

    companion object {
        private const val EVENT_ID = "enum-value-added-later"
        private const val VALUE_ADDED_LATER = "VALUE_ADDED_LATER"
    }
}
