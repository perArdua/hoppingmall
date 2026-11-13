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
    }
}
