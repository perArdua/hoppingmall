package com.hoppingmall.notification.domain

import com.hoppingmall.notification.config.JpaConfig
import com.hoppingmall.notification.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@DisplayName("MySQL 스키마의 enum 열 타입")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig::class)
class EnumColumnTypeMySqlIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun enumColumns(): List<String> =
        jdbcTemplate.queryForList(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' nullable=', IS_NULLABLE, ' default=', IFNULL(COLUMN_DEFAULT, 'NULL'))
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'enum'
            ORDER BY TABLE_NAME, COLUMN_NAME
            """.trimIndent(),
            String::class.java
        )

    @Test
    fun 스키마에_MySQL_ENUM_열이_하나도_없다() {
        assertThat(enumColumns()).isEmpty()
    }

    @Test
    fun 알림_종류_열은_VARCHAR로_만들어진다() {
        val columnType = jdbcTemplate.queryForObject(
            """
            SELECT COLUMN_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'type'
            """.trimIndent(),
            String::class.java
        )

        assertThat(columnType).isEqualTo("varchar(32)")
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
    fun enum에_없는_새_값도_알림_종류_열에_저장된다() {
        jdbcTemplate.update(
            "INSERT INTO notifications (created_at, event_id, user_id, type, title, content, is_read) VALUES (NOW(6), ?, 1, ?, 'title', 'content', false)",
            EVENT_ID,
            VALUE_ADDED_LATER
        )

        assertThat(jdbcTemplate.queryForObject("SELECT type FROM notifications WHERE event_id = ?", String::class.java, EVENT_ID))
            .isEqualTo(VALUE_ADDED_LATER)
    }

    companion object {
        private const val EVENT_ID = "enum-value-added-later"
        private const val VALUE_ADDED_LATER = "VALUE_ADDED_LATER"
        private const val DATABASE = "notification_mysql_test"

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, DATABASE)
        }
    }
}
