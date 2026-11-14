package com.hoppingmall.order.support

import com.hoppingmall.order.config.JpaConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@DisplayName("MySQL 스키마의 enum 필드 열 타입")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class EnumColumnTypeMySqlTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun 주문_서비스_엔티티의_표에는_MySQL_ENUM_열이_없다() {
        val orderTables = entityManager.metamodel.entities
            .map { it.javaType }
            .filter { it.packageName.startsWith(ORDER_PACKAGE) }
            .mapNotNull { it.getAnnotation(Table::class.java)?.name }
            .toSet()

        assertThat(orderTables).contains("orders", "refunds", "shippings")
        assertThat(enumColumns().filter { it.substringBefore('.') in orderTables }).isEmpty()
    }

    @Test
    fun 스키마의_모든_표에_MySQL_ENUM_열이_없다() {
        assertThat(enumColumns()).isEmpty()
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
    fun enum에_없는_새_값도_배송_상태_열에_저장된다() {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO shippings (created_at, order_id, buyer_id, status, carrier_name, tracking_number,
                    recipient_name, recipient_phone, recipient_address)
                VALUES (NOW(6), 1, 1, ?, 'carrier', ?, 'name', '010', 'address')
                """.trimIndent(),
                VALUE_ADDED_LATER,
                TRACKING_NUMBER
            )

            assertThat(jdbcTemplate.queryForObject("SELECT status FROM shippings WHERE tracking_number = ?", String::class.java, TRACKING_NUMBER))
                .isEqualTo(VALUE_ADDED_LATER)
        } finally {
            jdbcTemplate.update("DELETE FROM shippings WHERE tracking_number = ?", TRACKING_NUMBER)
        }
    }

    private fun enumColumns(): List<String> =
        jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND DATA_TYPE = 'enum'
            ORDER BY TABLE_NAME, COLUMN_NAME
            """.trimIndent(),
            String::class.java
        )

    companion object {
        private const val ORDER_PACKAGE = "com.hoppingmall.order"
        private const val TRACKING_NUMBER = "enum-value-added-later"
        private const val VALUE_ADDED_LATER = "VALUE_ADDED_LATER"
    }
}
