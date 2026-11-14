package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
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
import java.util.concurrent.TimeUnit

@DisplayName("보관이 꺼져 있을 때 처리 완료된 DLQ 메시지 퍼지 (MySQL)")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [DLQRetentionPurgeMySqlBatchTest.TestConfig::class])
class DLQRetentionPurgeMySqlBatchTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var dlqRetentionPurgeScheduler: DLQRetentionPurgeScheduler

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM dlq_messages")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM dlq_messages")
    }

    @Test
    fun 처리_완료된_지_30일이_지난_PROCESSED_메시지만_보관_없이_한_트랜잭션에_1000건이_넘지_않게_나눠_지운다() {
        val now = System.currentTimeMillis()
        val old = now - TimeUnit.DAYS.toMillis(31)
        val recent = now - TimeUnit.DAYS.toMillis(29)
        dlqMessageRepository.saveAll((1..2500).map { message(it.toLong(), DLQStatus.PROCESSED, old) })
        dlqMessageRepository.saveAll(
            (1..5).map { message(10_000L + it, DLQStatus.FAILED, old) } +
                (1..5).map { message(20_000L + it, DLQStatus.PROCESSED, recent) } +
                (1..5).map { message(30_000L + it, DLQStatus.FAILED, recent) } +
                (1..5).map { message(40_000L + it, DLQStatus.PENDING, null) } +
                (1..5).map { message(50_000L + it, DLQStatus.RETRYING, null) }
        )
        startStatementHistory()

        dlqRetentionPurgeScheduler.purgeFinishedMessages()

        val deletedPerTransaction = deletedRowsPerTransaction()
        assertThat(deletedPerTransaction.sum()).isEqualTo(2500L)
        assertThat(deletedPerTransaction).allMatch { it <= 1000L }
        assertThat(deletedPerTransaction.count { it > 0L }).isGreaterThanOrEqualTo(3)
        assertThat(countByStatus(DLQStatus.PROCESSED)).isEqualTo(5L)
        assertThat(countByStatus(DLQStatus.FAILED)).isEqualTo(10L)
        assertThat(countByStatus(DLQStatus.PENDING)).isEqualTo(5L)
        assertThat(countByStatus(DLQStatus.RETRYING)).isEqualTo(5L)
    }

    @Test
    fun FAILED_메시지는_보존_기간이_지나도_보관_없이_지우지_않는다() {
        val old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31)
        val failed = message(60_001L, DLQStatus.PENDING, null).also { it.markAsFailed("최대 재시도 횟수 초과") }
        dlqMessageRepository.saveAll(listOf(failed, message(60_002L, DLQStatus.FAILED, old)))
        jdbcTemplate.update(
            "UPDATE dlq_messages SET processed_at = processed_at - ? WHERE original_offset = 60001 AND processed_at IS NOT NULL",
            TimeUnit.DAYS.toMillis(31)
        )

        dlqRetentionPurgeScheduler.purgeFinishedMessages()

        assertThat(countByStatus(DLQStatus.FAILED)).isEqualTo(2L)
    }

    @Test
    fun 처리_완료된_메시지_삭제는_상태와_끝난_시각_인덱스로_범위만_읽는다() {
        val now = System.currentTimeMillis()
        dlqMessageRepository.saveAll((1..2000).map { message(100_000L + it, DLQStatus.PENDING, null) })
        dlqMessageRepository.saveAll((1..20).map { message(200_000L + it, DLQStatus.PROCESSED, now) })
        jdbcTemplate.execute("ANALYZE TABLE dlq_messages")

        val plan = jdbcTemplate.queryForMap(
            "EXPLAIN DELETE FROM dlq_messages WHERE status = ? AND processed_at < ? LIMIT 1000",
            DLQStatus.PROCESSED.name,
            now - TimeUnit.DAYS.toMillis(30)
        )

        assertThat(plan["key"]).isEqualTo("idx_dlq_status_processed_at")
        assertThat(plan["type"]).isEqualTo("range")
    }

    private fun countByStatus(status: DLQStatus): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dlq_messages WHERE status = ?", Long::class.java, status.name
        )!!

    private fun message(offset: Long, status: DLQStatus, processedAt: Long?): DLQMessage =
        DLQMessage(
            originalTopic = "payment",
            originalPartition = 0,
            originalOffset = offset,
            originalKey = "key",
            originalValue = "{}",
            exceptionMessage = "error",
            errorTimestamp = 1L,
            status = status,
            processedAt = processedAt
        )

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
    @EntityScan(basePackageClasses = [DLQMessage::class])
    @EnableJpaRepositories(basePackageClasses = [DLQMessageRepository::class])
    @Import(DLQRetentionPurgeScheduler::class)
    class TestConfig

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
