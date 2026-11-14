package com.hoppingmall.outbox.domain

import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@DisplayName("아웃박스 엔티티의 MySQL 스키마")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [OutboxEventMySqlSchemaTest.TestConfig::class])
class OutboxEventMySqlSchemaTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Test
    fun 스키마에_MySQL_ENUM_열이_없다() {
        val columns = jdbcTemplate.queryForList(
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
        )

        assertThat(columns.map { it["TABLE_NAME"] }).contains("outbox_events")
        assertThat(columns.filter { it["DATA_TYPE"] == "enum" }).isEmpty()
    }

    @Test
    fun 상태_열은_길이_32의_VARCHAR이고_상태를_저장하고_읽는다() {
        val status = jdbcTemplate.queryForMap(
            "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'status'"
        )
        val saved = outboxEventRepository.saveAndFlush(
            OutboxEvent(
                aggregateType = "Payment",
                aggregateId = "1",
                eventType = "PaymentCompleted",
                eventData = "{}",
                topic = "payment",
                status = OutboxStatus.RETRYING
            )
        )

        assertThat(status["DATA_TYPE"]).isEqualTo("varchar")
        assertThat((status["CHARACTER_MAXIMUM_LENGTH"] as Number).toLong()).isEqualTo(32L)
        assertThat(status["IS_NULLABLE"]).isEqualTo("NO")
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String::class.java, saved.id))
            .isEqualTo("RETRYING")
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.RETRYING)).isEqualTo(1L)
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
        val saved = outboxEventRepository.saveAndFlush(
            OutboxEvent(
                aggregateType = "Payment",
                aggregateId = "2",
                eventType = "PaymentCompleted",
                eventData = "{}",
                topic = "payment"
            )
        )

        jdbcTemplate.update("UPDATE outbox_events SET status = ? WHERE id = ?", "VALUE_ADDED_LATER", saved.id)

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String::class.java, saved.id))
            .isEqualTo("VALUE_ADDED_LATER")
    }

    @Configuration
    @EntityScan(basePackageClasses = [OutboxEvent::class])
    @EnableJpaRepositories(basePackageClasses = [OutboxEventRepository::class])
    class TestConfig

    companion object {
        private val mysql: MySQLContainer<*> by lazy {
            MySQLContainer("mysql:8.0.44").also { it.start() }
        }

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
            registry.add("spring.datasource.hikari.maximum-pool-size") { 2 }
            registry.add("spring.datasource.hikari.minimum-idle") { 0 }
        }
    }
}
