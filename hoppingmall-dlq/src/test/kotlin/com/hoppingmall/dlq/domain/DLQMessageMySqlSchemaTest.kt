package com.hoppingmall.dlq.domain

import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
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

@DisplayName("DLQ 엔티티의 MySQL 스키마")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [DLQMessageMySqlSchemaTest.TestConfig::class])
class DLQMessageMySqlSchemaTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Test
    fun 스키마에_MySQL_ENUM_열이_없다() {
        val columns = jdbcTemplate.queryForList(
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
        )

        assertThat(columns.map { it["TABLE_NAME"] }).contains("dlq_messages")
        assertThat(columns.filter { it["DATA_TYPE"] == "enum" }).isEmpty()
    }

    @Test
    fun 상태_열은_길이_20의_VARCHAR이고_상태를_저장하고_읽는다() {
        val status = jdbcTemplate.queryForMap(
            "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dlq_messages' AND COLUMN_NAME = 'status'"
        )
        val saved = dlqMessageRepository.saveAndFlush(
            DLQMessage(
                originalTopic = "payment",
                originalPartition = 0,
                originalOffset = 1L,
                originalKey = "key",
                originalValue = "{}",
                exceptionMessage = "error",
                errorTimestamp = 1L,
                status = DLQStatus.PROCESSED
            )
        )

        assertThat(status["DATA_TYPE"]).isEqualTo("varchar")
        assertThat((status["CHARACTER_MAXIMUM_LENGTH"] as Number).toLong()).isEqualTo(20L)
        assertThat(status["IS_NULLABLE"]).isEqualTo("NO")
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM dlq_messages WHERE id = ?", String::class.java, saved.id))
            .isEqualTo("PROCESSED")
        assertThat(dlqMessageRepository.countByStatus(DLQStatus.PROCESSED)).isEqualTo(1L)
    }

    @Configuration
    @EntityScan(basePackageClasses = [DLQMessage::class])
    @EnableJpaRepositories(basePackageClasses = [DLQMessageRepository::class])
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
