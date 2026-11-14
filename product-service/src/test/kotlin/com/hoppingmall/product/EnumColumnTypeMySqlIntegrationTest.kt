package com.hoppingmall.product

import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("enum 필드 열의 MySQL 타입")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class EnumColumnTypeMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun 상품_서비스_엔티티_표의_enum_필드는_VARCHAR_열로_만들어진다() {
        val productTables = productTables()

        assertThat(columnType("products", "status")).isEqualTo("varchar")
        assertThat(columnType("bulk_import_jobs", "status")).isEqualTo("varchar")
        assertThat(columnType("inventory_reservations", "status")).isEqualTo("varchar")
        assertThat(enumColumns().filter { it.substringBefore('.') in productTables }).isEmpty()
    }

    @Test
    fun 스키마의_어느_표에도_MySQL_ENUM_열이_없다() {
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
    fun enum에_없는_새_값도_대량_등록_작업_상태_열에_저장된다() {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO bulk_import_jobs (created_at, seller_id, file_name, status, total_rows, processed_rows, success_count, fail_count)
                VALUES (NOW(6), 1, ?, ?, 0, 0, 0, 0)
                """.trimIndent(),
                FILE_NAME,
                VALUE_ADDED_LATER
            )

            assertThat(jdbcTemplate.queryForObject("SELECT status FROM bulk_import_jobs WHERE file_name = ?", String::class.java, FILE_NAME))
                .isEqualTo(VALUE_ADDED_LATER)
        } finally {
            jdbcTemplate.update("DELETE FROM bulk_import_jobs WHERE file_name = ?", FILE_NAME)
        }
    }

    private fun productTables(): Set<String> =
        entityManagerFactory.metamodel.entities
            .map { it.javaType }
            .filter { it.packageName.startsWith("com.hoppingmall.product") }
            .map { it.getAnnotation(Table::class.java).name }
            .toSet()

    private fun columnType(table: String, column: String): String? =
        jdbcTemplate.queryForObject(
            """
            SELECT DATA_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
            """.trimIndent(),
            String::class.java,
            table,
            column
        )

    private fun enumColumns(): List<String> =
        jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'enum'
            ORDER BY TABLE_NAME, COLUMN_NAME
            """.trimIndent(),
            String::class.java
        )

    companion object {
        private const val FILE_NAME = "enum-value-added-later.csv"
        private const val VALUE_ADDED_LATER = "VALUE_ADDED_LATER"
    }
}
