package com.hoppingmall.outbox.scheduler

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDateTime

@DisplayName("아웃박스 처리 완료 이벤트 정리의 MySQL 배치 삭제")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [OutboxCleanupMySqlBatchTest.TestConfig::class])
class OutboxCleanupMySqlBatchTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var outboxMaintenanceScheduler: OutboxMaintenanceScheduler

    @Test
    fun 오래된_처리_완료_이벤트_3000건을_한_트랜잭션에_1000건이_넘지_않게_나눠_지운다() {
        val old = LocalDateTime.now().minusDays(8)
        val recent = LocalDateTime.now().minusDays(1)
        outboxEventRepository.saveAll((1..3000).map { event(processed = true, processedAt = old) })
        outboxEventRepository.saveAll(
            (1..5).map { event(processed = true, processedAt = recent) } +
                (1..5).map { event(processed = false, processedAt = null) }
        )
        startStatementHistory()

        outboxMaintenanceScheduler.cleanupProcessedEvents()

        val deletedPerTransaction = deletedRowsPerTransaction()
        assertThat(deletedPerTransaction.sum()).isEqualTo(3000L)
        assertThat(deletedPerTransaction).allMatch { it <= 1000L }
        assertThat(deletedPerTransaction.count { it > 0L }).isGreaterThanOrEqualTo(3)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Long::class.java)).isEqualTo(10L)
    }

    @Test
    fun 처리_완료_이벤트_삭제는_처리_여부와_처리_시각_인덱스로_범위만_읽는다() {
        val recent = LocalDateTime.now().minusDays(1)
        try {
            outboxEventRepository.saveAll((1..2000).map { event(processed = true, processedAt = recent) })
            jdbcTemplate.execute("ANALYZE TABLE outbox_events")

            val plan = jdbcTemplate.queryForMap(
                "EXPLAIN DELETE FROM outbox_events WHERE processed = true AND processed_at < ? LIMIT 1000",
                LocalDateTime.now().minusDays(7)
            )

            assertThat(plan["key"]).isEqualTo("idx_outbox_processed_processed_at")
            assertThat(plan["type"]).isEqualTo("range")
        } finally {
            jdbcTemplate.update("DELETE FROM outbox_events")
        }
    }

    private fun event(processed: Boolean, processedAt: LocalDateTime?): OutboxEvent =
        OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = "{}",
            topic = "payment",
            processedAt = processedAt
        ).also { it.processed = processed }

    private fun startStatementHistory() {
        jdbcTemplate.update("TRUNCATE TABLE performance_schema.events_statements_history_long")
        jdbcTemplate.update(
            "UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' " +
                "WHERE NAME IN ('events_statements_history_long', 'events_transactions_current')"
        )
    }

    private fun deletedRowsPerTransaction(): List<Long> {
        val statements = jdbcTemplate.queryForList(
            "SELECT EVENT_NAME, SQL_TEXT, ROWS_AFFECTED FROM performance_schema.events_statements_history_long " +
                "WHERE CURRENT_SCHEMA = DATABASE() ORDER BY THREAD_ID, EVENT_ID"
        )
        val perTransaction = mutableListOf<Long>()
        var current: Long? = null
        statements.forEach { statement ->
            val eventName = statement["EVENT_NAME"] as String
            val sqlText = (statement["SQL_TEXT"] as String?).orEmpty().trim().lowercase()
            when {
                sqlText.startsWith("set autocommit=0") -> current = 0L
                eventName == "statement/sql/commit" || eventName == "statement/sql/rollback" -> {
                    current?.let { perTransaction.add(it) }
                    current = null
                }
                eventName in DELETE_EVENTS -> {
                    val rows = (statement["ROWS_AFFECTED"] as Number).toLong()
                    if (current == null) perTransaction.add(rows) else current = current!! + rows
                }
            }
        }
        current?.let { perTransaction.add(it) }
        return perTransaction
    }

    @Configuration
    @EntityScan(basePackageClasses = [OutboxEvent::class])
    @EnableJpaRepositories(basePackageClasses = [OutboxEventRepository::class])
    @Import(OutboxMaintenanceScheduler::class)
    class TestConfig {
        @Bean
        fun outboxEventPublisher(): OutboxEventPublisher = Mockito.mock(OutboxEventPublisher::class.java)
    }

    companion object {
        private val DELETE_EVENTS = setOf("statement/sql/delete", "statement/sql/delete_multi")

        private val mysql: MySQLContainer<*> by lazy {
            val container: MySQLContainer<*> = MySQLContainer("mysql:8.0.44")
            container.withUsername("root")
            container.withCommand("--performance-schema-events-statements-history-long-size=20000")
            container.also { it.start() }
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
