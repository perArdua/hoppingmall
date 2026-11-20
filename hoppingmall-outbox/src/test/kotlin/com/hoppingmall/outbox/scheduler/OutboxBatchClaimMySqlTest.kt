package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("아웃박스 일괄 선점의 MySQL 잠금 선점")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [OutboxBatchClaimMySqlTest.TestConfig::class])
class OutboxBatchClaimMySqlTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val sendCounts = ConcurrentHashMap<String, AtomicInteger>()

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM outbox_events")
        sendCounts.clear()
    }

    @RepeatedTest(5)
    fun 서로_다른_발행기_두_개가_동시에_돌아도_행마다_정확히_한_번_발행한다() {
        insertPending(1000)
        val kafkas = listOf(countingKafka(), countingKafka())
        val publishers = kafkas.map { publisher(it.template) }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        val futures = publishers.map { publisher ->
            pool.submit {
                start.await()
                publisher.publishPendingEvents()
            }
        }
        start.countDown()
        futures.forEach { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(sendCounts).hasSize(1000)
        assertThat(sendCounts.values.map { it.get() }).allMatch { it == 1 }
        assertThat(countByStatus("PUBLISHED")).isEqualTo(1000L)
        assertThat(countByStatus("RETRYING") + countByStatus("PENDING")).isZero()
    }

    @Test
    fun 한_번_깨어나면_꽉_찬_배치_뒤에_남은_행까지_이어서_발행한다() {
        insertPending(300)
        startStatementHistory()
        val kafka = countingKafka()

        publisher(kafka.template).publishPendingEvents()

        assertThat(countByStatus("PUBLISHED")).isEqualTo(300L)
        assertThat(sendCounts).hasSize(300)
        assertThat(kafka.transactions.get()).isEqualTo(3)
        val lockStatements = jdbcTemplate.queryForList(
            "SELECT SQL_TEXT FROM performance_schema.events_statements_history_long " +
                "WHERE CURRENT_SCHEMA = DATABASE() AND SQL_TEXT LIKE '%SKIP LOCKED%' ORDER BY EVENT_ID",
            String::class.java
        )
        assertThat(lockStatements).hasSize(4)
    }

    @Test
    fun Kafka_전송이_실패한_배치_뒤에는_이어_돌지_않는다() {
        insertPending(250)
        val kafka = countingKafka(fail = true)

        publisher(kafka.template).publishPendingEvents()

        assertThat(kafka.transactions.get()).isEqualTo(1)
        assertThat(countByStatus("FAILED")).isEqualTo(100L)
        assertThat(countByStatus("PENDING")).isEqualTo(150L)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = 'FAILED' AND retry_count = 1 AND processed = false",
                Long::class.java
            )
        ).isEqualTo(100L)
    }

    private fun insertPending(count: Int) {
        val base = LocalDateTime.now().minusMinutes(1)
        jdbcTemplate.batchUpdate(
            "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, event_data, topic, partition_key, " +
                "status, retry_count, created_at, updated_at, processed) VALUES ('Payment', ?, 'PaymentCompleted', '{}', " +
                "'payment', ?, 'PENDING', 0, ?, ?, false)",
            (1..count).map { i ->
                val at = Timestamp.valueOf(base.plusNanos(i * 1000L))
                arrayOf<Any>(i.toString(), "k$i", at, at)
            }
        )
    }

    private fun countByStatus(status: String): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE status = ?", Long::class.java, status)!!

    private fun publisher(kafkaTemplate: KafkaTemplate<String, Any>): OutboxEventPublisher {
        val converter = Mockito.mock(AvroEventConverter::class.java)
        whenever(converter.convertJsonToAvro(any(), any())).thenReturn(Mockito.mock(GenericRecord::class.java))
        return OutboxEventPublisher(
            outboxEventRepository,
            kafkaTemplate,
            Mockito.mock(OutboxMetrics::class.java),
            converter,
            transactionManager
        ).also { ReflectionTestUtils.setField(it, "batchClaim", true) }
    }

    private class CountingKafka(
        val template: KafkaTemplate<String, Any>,
        val transactions: AtomicInteger,
        val sent: AtomicInteger
    )

    @Suppress("UNCHECKED_CAST")
    private fun countingKafka(fail: Boolean = false): CountingKafka {
        val template = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val transactions = AtomicInteger()
        val sent = AtomicInteger()
        whenever(template.send(any<String>(), any<String>(), any())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val key = invocation.arguments[1] as String
            sendCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
            sent.incrementAndGet()
            CompletableFuture.completedFuture(
                SendResult<String, Any>(
                    ProducerRecord(topic, key, invocation.arguments[2]),
                    RecordMetadata(TopicPartition(topic, 0), 0L, 0, System.currentTimeMillis(), 0, 0)
                )
            )
        }
        whenever(template.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>()))
            .thenAnswer { invocation ->
                transactions.incrementAndGet()
                if (fail) throw RuntimeException("broker down")
                (invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>).doInOperations(template)
            }
        return CountingKafka(template, transactions, sent)
    }

    private fun startStatementHistory() {
        jdbcTemplate.update("TRUNCATE TABLE performance_schema.events_statements_history_long")
        jdbcTemplate.update(
            "UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' WHERE NAME = 'events_statements_history_long'"
        )
    }

    @Configuration
    @EntityScan(basePackageClasses = [OutboxEvent::class])
    @EnableJpaRepositories(basePackageClasses = [OutboxEventRepository::class])
    class TestConfig

    companion object {
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
            registry.add("spring.datasource.hikari.maximum-pool-size") { 4 }
            registry.add("spring.datasource.hikari.minimum-idle") { 0 }
        }
    }
}
