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
}
