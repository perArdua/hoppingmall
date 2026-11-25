package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

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

    @Autowired
    private lateinit var dataSource: DataSource

    private val sendCounts = ConcurrentHashMap<String, AtomicInteger>()

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM outbox_events")
        sendCounts.clear()
    }

    @RepeatedTest(2)
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
    fun 연결된_DB가_MySQL이면_기동_판별_뒤에도_잠금_조회로_일괄_선점한다() {
        insertPending(150)
        startStatementHistory()
        val kafka = countingKafka()
        val converter = Mockito.mock(AvroEventConverter::class.java)
        whenever(converter.convertJsonToAvro(any(), any())).thenReturn(Mockito.mock(GenericRecord::class.java))
        val publisher = OutboxEventPublisher(
            outboxEventRepository,
            kafka.template,
            Mockito.mock(OutboxMetrics::class.java),
            converter,
            transactionManager,
            OutboxBrokerBackoff(Mockito.mock(OutboxMetrics::class.java)),
            Clock.systemDefaultZone(),
            dataSource
        )

        publisher.configureClaimMode()
        publisher.publishPendingEvents()

        assertThat(ReflectionTestUtils.getField(publisher, "batchClaim")).isEqualTo(true)
        assertThat(countByStatus("PUBLISHED")).isEqualTo(150L)
        assertThat(kafka.transactions.get()).isEqualTo(2)
        val statements = jdbcTemplate.queryForList(
            "SELECT SQL_TEXT FROM performance_schema.events_statements_history_long " +
                "WHERE CURRENT_SCHEMA = DATABASE() AND SQL_TEXT LIKE '%outbox_events%' ORDER BY EVENT_ID",
            String::class.java
        )
        assertThat(statements.filter { it.contains("SKIP LOCKED") }).hasSize(2)
        assertThat(statements.filter { it.contains("FORCE INDEX (PRIMARY)") }).hasSize(4)
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
    fun 브로커가_죽어_실패한_배치는_재시도를_쓰지_않고_PENDING으로_되돌린_뒤_이어_돌지_않고_물러난다() {
        insertPending(250)
        val kafka = countingKafka(fail = true)
        val publisher = publisher(kafka.template)

        publisher.publishPendingEvents()
        publisher.publishPendingEvents()

        assertThat(kafka.transactions.get()).isEqualTo(1)
        assertThat(publisher.isBrokerBackingOff()).isTrue()
        assertThat(countByStatus("FAILED")).isZero()
        assertThat(countByStatus("RETRYING")).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING' AND retry_count = 0 AND processed = false",
                Long::class.java
            )
        ).isEqualTo(250L)
    }

    @Test
    fun 선점_뒤_한도를_넘긴_배치는_Kafka에_한_건도_보내지_않고_재시도_없이_PENDING으로_되돌린다() {
        insertPending(50)
        val kafka = transactionalKafka()

        guardedPublisher(kafka.template, TestClock(Instant.now(), stepMillis = 1_000), maxClaimAgeMs = 500)
            .publishPendingEvents()

        assertThat(kafka.transactions.get()).isEqualTo(1)
        assertThat(kafka.aborted.get()).isEqualTo(1)
        assertThat(kafka.attempted.get()).isZero()
        assertThat(kafka.committed).isEmpty()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING' AND retry_count = 0 AND processed = false",
                Long::class.java
            )
        ).isEqualTo(50L)
    }

    @Test
    fun 선점_뒤_한도를_넘긴_배치_뒤에는_꽉_찬_배치여도_이어_돌지_않는다() {
        insertPending(300)
        val kafka = transactionalKafka()

        guardedPublisher(kafka.template, TestClock(Instant.now(), stepMillis = 1_000), maxClaimAgeMs = 500)
            .publishPendingEvents()

        assertThat(kafka.transactions.get()).isEqualTo(1)
        assertThat(kafka.committed).isEmpty()
        assertThat(countByStatus("RETRYING")).isZero()
        assertThat(countByStatus("PENDING")).isEqualTo(300L)
    }

    @Test
    fun 전송_뒤_커밋_전에_한도를_넘기면_Kafka_트랜잭션을_버리고_재시도_없이_PENDING으로_되돌린다() {
        insertPending(20)
        val clock = TestClock(Instant.now())
        val kafka = transactionalKafka(onSend = { clock.advanceMillis(1_000) })

        guardedPublisher(kafka.template, clock, maxClaimAgeMs = 500).publishPendingEvents()

        assertThat(kafka.attempted.get()).isEqualTo(20)
        assertThat(kafka.aborted.get()).isEqualTo(1)
        assertThat(kafka.committed).isEmpty()
        assertThat(countByStatus("PENDING")).isEqualTo(20L)
        assertThat(countByStatus("PUBLISHED")).isZero()
    }

    @Test
    fun 선점이_한도를_넘긴_뒤_Kafka_트랜잭션이_실패해도_행을_FAILED로_바꾸지_않는다() {
        insertPending(30)
        val clock = TestClock(Instant.now())
        @Suppress("UNCHECKED_CAST")
        val template = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        whenever(template.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>()))
            .thenAnswer {
                clock.advanceMillis(1_000)
                throw RuntimeException("producer fenced")
            }

        guardedPublisher(template, clock, maxClaimAgeMs = 500).publishPendingEvents()

        assertThat(countByStatus("PENDING")).isEqualTo(30L)
        assertThat(countByStatus("FAILED")).isZero()
    }

    @Test
    fun 전체_장애에서_격리_회차가_전송마다_60초씩_걸려도_선점한_행을_모두_재시도_없이_PENDING으로_되돌린다() {
        insertPending(100)
        val clock = TestClock(Instant.now())
        val transactions = AtomicInteger()
        @Suppress("UNCHECKED_CAST")
        val template = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        whenever(template.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>()))
            .thenAnswer {
                transactions.incrementAndGet()
                clock.advanceMillis(60_000)
                throw TimeoutException("Topic payment not present in metadata after 60000 ms.")
            }
        val backoff = OutboxBrokerBackoff(Mockito.mock(OutboxMetrics::class.java), clock = clock)
        repeat(3) { backoff.recordFailure() }
        clock.advanceMillis(60_000)

        guardedPublisher(template, clock, maxClaimAgeMs = 300_000, backoff = backoff).publishPendingEvents()

        assertThat(transactions.get()).isEqualTo(2)
        assertThat(backoff.isBackingOff()).isTrue()
        assertThat(countByStatus("RETRYING")).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING' AND retry_count = 0 AND processed = false",
                Long::class.java
            )
        ).isEqualTo(100L)
    }

    @Test
    fun 선점_뒤_한도_안이면_기존처럼_모두_한_번씩_발행한다() {
        insertPending(150)
        val kafka = transactionalKafka()

        guardedPublisher(kafka.template, TestClock(Instant.now()), maxClaimAgeMs = 500).publishPendingEvents()

        assertThat(kafka.transactions.get()).isEqualTo(2)
        assertThat(kafka.aborted.get()).isZero()
        assertThat(kafka.committed).hasSize(150)
        assertThat(kafka.committed.values.map { it.get() }).allMatch { it == 1 }
        assertThat(countByStatus("PUBLISHED")).isEqualTo(150L)
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
            transactionManager,
            OutboxBrokerBackoff(Mockito.mock(OutboxMetrics::class.java))
        )
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
                if (fail) throw KafkaException("Failed to construct kafka producer")
                (invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>).doInOperations(template)
            }
        return CountingKafka(template, transactions, sent)
    }

    private fun guardedPublisher(
        kafkaTemplate: KafkaTemplate<String, Any>,
        clock: Clock,
        maxClaimAgeMs: Long,
        backoff: OutboxBrokerBackoff = OutboxBrokerBackoff(Mockito.mock(OutboxMetrics::class.java))
    ): OutboxEventPublisher {
        val converter = Mockito.mock(AvroEventConverter::class.java)
        whenever(converter.convertJsonToAvro(any(), any())).thenReturn(Mockito.mock(GenericRecord::class.java))
        return OutboxEventPublisher(
            outboxEventRepository,
            kafkaTemplate,
            Mockito.mock(OutboxMetrics::class.java),
            converter,
            transactionManager,
            backoff,
            clock
        ).also {
            ReflectionTestUtils.setField(it, "maxClaimAgeMs", maxClaimAgeMs)
        }
    }

    private class TransactionalKafka(
        val template: KafkaTemplate<String, Any>,
        val transactions: AtomicInteger,
        val aborted: AtomicInteger,
        val attempted: AtomicInteger,
        val committed: ConcurrentHashMap<String, AtomicInteger>
    )

    @Suppress("UNCHECKED_CAST")
    private fun transactionalKafka(onSend: () -> Unit = {}): TransactionalKafka {
        val template = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val transactions = AtomicInteger()
        val aborted = AtomicInteger()
        val attempted = AtomicInteger()
        val committed = ConcurrentHashMap<String, AtomicInteger>()
        val inFlight = mutableListOf<String>()
        whenever(template.send(any<String>(), any<String>(), any())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val key = invocation.arguments[1] as String
            onSend()
            attempted.incrementAndGet()
            inFlight.add(key)
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
                inFlight.clear()
                try {
                    val result = (invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>)
                        .doInOperations(template)
                    inFlight.forEach { key -> committed.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet() }
                    result
                } catch (e: Exception) {
                    aborted.incrementAndGet()
                    throw e
                } finally {
                    inFlight.clear()
                }
            }
        return TransactionalKafka(template, transactions, aborted, attempted, committed)
    }

    private class TestClock(start: Instant, private val stepMillis: Long = 0) : Clock() {
        private val now = AtomicReference(start)
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now.getAndUpdate { it.plusMillis(stepMillis) }
        fun advanceMillis(millis: Long) {
            now.updateAndGet { it.plusMillis(millis) }
        }
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
