package com.hoppingmall.outbox.scheduler

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import jakarta.persistence.EntityManager
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
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
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@DisplayName("아웃박스 선점 시각 조건부 완료·실패 표시의 MySQL 동작")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [OutboxClaimedMarkMySqlTest.TestConfig::class])
class OutboxClaimedMarkMySqlTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var entityManager: EntityManager

    private val otherClaimAt: LocalDateTime = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MICROS)

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM outbox_events")
    }

    @Test
    fun 테스트_표의_updated_at은_마이크로초_DATETIME이다() {
        val column = jdbcTemplate.queryForMap(
            "SELECT DATA_TYPE, DATETIME_PRECISION FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'updated_at'"
        )

        assertThat(column["DATA_TYPE"]).isEqualTo("datetime")
        assertThat((column["DATETIME_PRECISION"] as Number).toInt()).isEqualTo(6)
    }

    @Test
    fun 완료_표시_직전에_다른_쪽이_다시_선점한_행은_완료로_바꾸지_않는다() {
        insert(keys("k1", "k2", "k3", "k4", "k5"))
        val kafka = fakeKafka(beforeCommit = { reclaim("k3") })

        val warnings = captureWarnings { batchPublisher(kafka.template).publishPendingEvents() }

        assertThat(kafka.committed.keys).containsExactlyInAnyOrder("k1", "k2", "k3", "k4", "k5")
        assertThat(countByStatus("PUBLISHED")).isEqualTo(4L)
        val stolen = row("k3")
        assertThat(stolen["status"]).isEqualTo("RETRYING")
        assertThat(stolen["processed"]).isEqualTo(false)
        assertThat(stolen["processed_at"]).isNull()
        assertThat(time(stolen["updated_at"])).isEqualTo(otherClaimAt)
        assertThat(warnings).anyMatch { it.contains("marked 4 of 5 published events") }
    }

    @Test
    fun 실패_표시_직전에_다른_쪽이_다시_선점한_행은_실패로_바꾸지_않는다() {
        insert(keys("k1", "k2", "k3"))
        val kafka = fakeKafka(beforeCommit = { reclaim("k3") }, failWith = "broker down")

        batchPublisher(kafka.template).publishPendingEvents()

        assertThat(kafka.committed).isEmpty()
        listOf("k1", "k2").map(::row).forEach {
            assertThat(it["status"]).isEqualTo("FAILED")
            assertThat(it["retry_count"]).isEqualTo(1)
            assertThat(it["error_message"]).isEqualTo("broker down")
        }
        val stolen = row("k3")
        assertThat(stolen["status"]).isEqualTo("RETRYING")
        assertThat(stolen["retry_count"]).isEqualTo(0)
        assertThat(stolen["error_message"]).isNull()
        assertThat(time(stolen["updated_at"])).isEqualTo(otherClaimAt)
    }

    @Test
    fun 변환_실패_표시_직전에_다른_쪽이_다시_선점한_행은_실패로_바꾸지_않는다() {
        insert(keys("k1", "k2", "k3", "k4"))
        val converter = Mockito.mock(AvroEventConverter::class.java)
        whenever(converter.convertJsonToAvro(any(), any())).thenAnswer { invocation ->
            val data = invocation.arguments[1] as String
            when {
                data.contains("k3") -> {
                    reclaim("k3")
                    throw IllegalArgumentException("bad k3")
                }
                data.contains("k4") -> throw IllegalArgumentException("bad k4")
                else -> Mockito.mock(GenericRecord::class.java)
            }
        }
        val kafka = fakeKafka()

        batchPublisher(kafka.template, converter).publishPendingEvents()

        assertThat(kafka.committed.keys).containsExactlyInAnyOrder("k1", "k2")
        assertThat(row("k1")["status"]).isEqualTo("PUBLISHED")
        assertThat(row("k4")["status"]).isEqualTo("FAILED")
        assertThat(row("k4")["retry_count"]).isEqualTo(1)
        assertThat(row("k4")["error_message"]).isEqualTo("bad k4")
        val stolen = row("k3")
        assertThat(stolen["status"]).isEqualTo("RETRYING")
        assertThat(stolen["retry_count"]).isEqualTo(0)
        assertThat(stolen["error_message"]).isNull()
        assertThat(time(stolen["updated_at"])).isEqualTo(otherClaimAt)
    }

    @Test
    fun 정상_완료_뒤_행의_모든_열이_기존_save_방식과_같다() {
        val createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS)

        val (old, oldWindow) = runAndSnapshot(Seed("same", "PENDING", 0, createdAt)) {
            plainPublisher(fakeKafka().template).publishPendingEvents()
        }
        val (new, newWindow) = runAndSnapshot(Seed("same", "PENDING", 0, createdAt)) {
            batchPublisher(fakeKafka().template).publishPendingEvents()
        }

        assertSameExceptTimes(old, new)
        assertThat(new["status"]).isEqualTo("PUBLISHED")
        assertThat(new["processed"]).isEqualTo(true)
        assertTimeRule(old, oldWindow)
        assertTimeRule(new, newWindow)
    }

    @Test
    fun 실패_표시_뒤_행의_모든_열이_기존_save_방식과_같다() {
        listOf(0, 2).forEach { retry ->
            val createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS)
            val status = if (retry == 0) "PENDING" else "FAILED"

            val (old, _) = runAndSnapshot(Seed("same", status, retry, createdAt)) {
                plainPublisher(fakeKafka(failWith = "broker down").template).publishPendingEvents()
            }
            val (new, _) = runAndSnapshot(Seed("same", status, retry, createdAt)) {
                batchPublisher(fakeKafka(failWith = "broker down").template).publishPendingEvents()
            }

            assertSameExceptTimes(old, new)
            assertThat(new["retry_count"]).isEqualTo(retry + 1)
            assertThat(new["processed"]).isEqualTo(retry == 2)
            assertThat(new["processed_at"] != null).isEqualTo(retry == 2)
            assertThat(old["processed_at"] != null).isEqualTo(retry == 2)
        }
    }

    @Test
    fun 선점_UPDATE는_기존_JPQL과_같은_행을_같은_값으로_바꾸고_같은_수를_돌려준다() {
        val claimedAt = LocalDateTime.now().withNano(123_456_789).truncatedTo(ChronoUnit.MICROS)

        val (oldCount, oldRows) = claimAndSnapshot { ids ->
            entityManager.createQuery(LEGACY_CLAIM_JPQL)
                .setParameter("ids", ids)
                .setParameter("nextStatus", OutboxStatus.RETRYING)
                .setParameter("updatedAt", claimedAt)
                .setParameter("pendingStatus", OutboxStatus.PENDING)
                .setParameter("failedStatus", OutboxStatus.FAILED)
                .setParameter("maxRetries", 3)
                .executeUpdate()
        }
        val (newCount, newRows) = claimAndSnapshot { ids ->
            outboxEventRepository.claimEventsForPublish(
                ids = ids,
                nextStatus = OutboxStatus.RETRYING.name,
                updatedAt = claimedAt,
                pendingStatus = OutboxStatus.PENDING.name,
                failedStatus = OutboxStatus.FAILED.name,
                maxRetries = 3
            )
        }

        assertThat(newCount).isEqualTo(oldCount).isEqualTo(3)
        assertThat(newRows.keys).isEqualTo(oldRows.keys)
        newRows.forEach { (key, row) ->
            assertThat(row.filterKeys { it != "id" }).describedAs(key).isEqualTo(oldRows.getValue(key).filterKeys { it != "id" })
        }
        listOf("pending", "failed0", "failed2").forEach { key ->
            assertThat(newRows.getValue(key)["status"]).isEqualTo("RETRYING")
            assertThat(time(newRows.getValue(key)["updated_at"])).isEqualTo(claimedAt)
        }
        listOf("failed3", "retrying", "published", "pendingDone", "notListed").forEach { key ->
            assertThat(time(newRows.getValue(key)["updated_at"])).describedAs(key).isEqualTo(CLAIM_SEED_AT)
        }
    }

    private fun claimAndSnapshot(claim: (List<Long>) -> Int): Pair<Int, Map<String, Map<String, Any?>>> {
        jdbcTemplate.batchUpdate(
            "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, event_data, topic, partition_key, " +
                "status, retry_count, created_at, updated_at, processed) VALUES ('Payment', 'agg-1', 'PaymentCompleted', '{}', " +
                "'payment', ?, ?, ?, ?, ?, ?)",
            CLAIM_SEEDS.map { (key, status, retry, processed) ->
                arrayOf<Any>(key, status, retry, Timestamp.valueOf(CLAIM_SEED_AT), Timestamp.valueOf(CLAIM_SEED_AT), processed)
            }
        )
        val ids = jdbcTemplate.queryForList(
            "SELECT id FROM outbox_events WHERE partition_key <> 'notListed' ORDER BY id",
            Long::class.java
        )
        val count = TransactionTemplate(transactionManager).execute { claim(ids) }!!
        val rows = CLAIM_SEEDS.associate { it.key to row(it.key) }
        jdbcTemplate.update("DELETE FROM outbox_events")
        return count to rows
    }

    private data class ClaimSeed(val key: String, val status: String, val retryCount: Int, val processed: Boolean)

    private data class Seed(val key: String, val status: String, val retryCount: Int, val createdAt: LocalDateTime)

    private fun keys(vararg keys: String): List<Seed> {
        val base = LocalDateTime.now().minusMinutes(1)
        return keys.mapIndexed { i, key -> Seed(key, "PENDING", 0, base.plusNanos((i + 1) * 1000L)) }
    }

    private fun insert(seeds: List<Seed>, updatedAt: LocalDateTime? = null) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, event_data, topic, partition_key, " +
                "status, retry_count, created_at, updated_at, processed) VALUES ('Payment', 'agg-1', 'PaymentCompleted', ?, " +
                "'payment', ?, ?, ?, ?, ?, false)",
            seeds.map {
                arrayOf<Any>(
                    """{"key":"${it.key}"}""",
                    it.key,
                    it.status,
                    it.retryCount,
                    Timestamp.valueOf(it.createdAt),
                    Timestamp.valueOf(updatedAt ?: it.createdAt)
                )
            }
        )
    }

    private fun reclaim(key: String) {
        jdbcTemplate.update(
            "UPDATE outbox_events SET status = 'RETRYING', updated_at = ? WHERE partition_key = ?",
            Timestamp.valueOf(otherClaimAt),
            key
        )
    }

    private fun row(key: String): Map<String, Any?> =
        jdbcTemplate.queryForMap("SELECT * FROM outbox_events WHERE partition_key = ?", key)

    private fun countByStatus(status: String): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE status = ?", Long::class.java, status)!!

    private fun time(value: Any?): LocalDateTime? = when (value) {
        null -> null
        is Timestamp -> value.toLocalDateTime()
        else -> value as LocalDateTime
    }

    private fun runAndSnapshot(seed: Seed, action: () -> Unit): Pair<Map<String, Any?>, Pair<LocalDateTime, LocalDateTime>> {
        insert(listOf(seed))
        val start = LocalDateTime.now().minusSeconds(1)
        action()
        val end = LocalDateTime.now().plusSeconds(1)
        val snapshot = row(seed.key)
        jdbcTemplate.update("DELETE FROM outbox_events")
        return snapshot to (start to end)
    }

    private fun assertSameExceptTimes(old: Map<String, Any?>, new: Map<String, Any?>) {
        assertThat(new.keys).isEqualTo(old.keys)
        old.keys.filter { it !in TIME_RULE_COLUMNS && it != "id" }.forEach { column ->
            assertThat(new[column]).describedAs(column).isEqualTo(old[column])
        }
        TIME_RULE_COLUMNS.forEach { column ->
            assertThat(new[column] == null).describedAs(column).isEqualTo(old[column] == null)
        }
    }

    private fun assertTimeRule(row: Map<String, Any?>, window: Pair<LocalDateTime, LocalDateTime>) {
        val processedAt = time(row["processed_at"])!!
        val updatedAt = time(row["updated_at"])!!
        assertThat(processedAt).isBetween(window.first, window.second)
        assertThat(updatedAt).isBetween(window.first, window.second)
        assertThat(updatedAt).isAfterOrEqualTo(processedAt)
        assertThat(Duration.between(processedAt, updatedAt)).isLessThan(Duration.ofSeconds(1))
    }

    private fun batchPublisher(
        kafkaTemplate: KafkaTemplate<String, Any>,
        converter: AvroEventConverter = passingConverter(),
        clock: Clock = TestClock(Instant.now().plusNanos(789))
    ): OutboxEventPublisher =
        OutboxEventPublisher(
            outboxEventRepository,
            kafkaTemplate,
            Mockito.mock(OutboxMetrics::class.java),
            converter,
            transactionManager,
            clock
        ).also {
            ReflectionTestUtils.setField(it, "batchClaim", true)
        }

    private fun plainPublisher(
        kafkaTemplate: KafkaTemplate<String, Any>,
        clock: Clock = Clock.systemDefaultZone()
    ): OutboxEventPublisher =
        OutboxEventPublisher(
            outboxEventRepository,
            kafkaTemplate,
            Mockito.mock(OutboxMetrics::class.java),
            passingConverter(),
            transactionManager,
            clock
        )

    private fun passingConverter(): AvroEventConverter {
        val converter = Mockito.mock(AvroEventConverter::class.java)
        whenever(converter.convertJsonToAvro(any(), any())).thenReturn(Mockito.mock(GenericRecord::class.java))
        return converter
    }

    private class FakeKafka(
        val template: KafkaTemplate<String, Any>,
        val attempted: AtomicInteger,
        val committed: ConcurrentHashMap<String, AtomicInteger>
    )

    @Suppress("UNCHECKED_CAST")
    private fun fakeKafka(beforeCommit: () -> Unit = {}, failWith: String? = null): FakeKafka {
        val template = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
        val attempted = AtomicInteger()
        val committed = ConcurrentHashMap<String, AtomicInteger>()
        val inFlight = mutableListOf<String>()
        whenever(template.send(any<String>(), any<String>(), any())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val key = invocation.arguments[1] as String
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
                inFlight.clear()
                try {
                    val result = (invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>)
                        .doInOperations(template)
                    beforeCommit()
                    if (failWith != null) throw RuntimeException(failWith)
                    inFlight.forEach { key -> committed.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet() }
                    result
                } finally {
                    inFlight.clear()
                }
            }
        return FakeKafka(template, attempted, committed)
    }

    private fun captureWarnings(action: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }

    private class TestClock(start: Instant, private val stepMillis: Long = 0) : Clock() {
        private val now = AtomicReference(start)
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now.getAndUpdate { it.plusMillis(stepMillis) }
    }

    @Configuration
    @EntityScan(basePackageClasses = [OutboxEvent::class])
    @EnableJpaRepositories(basePackageClasses = [OutboxEventRepository::class])
    class TestConfig

    companion object {
        private val TIME_RULE_COLUMNS = setOf("processed_at", "updated_at")
        private val CLAIM_SEED_AT: LocalDateTime = LocalDateTime.of(2026, 11, 20, 0, 0, 0, 111_111_000)
        private val CLAIM_SEEDS = listOf(
            ClaimSeed("pending", "PENDING", 0, false),
            ClaimSeed("failed0", "FAILED", 0, false),
            ClaimSeed("failed2", "FAILED", 2, false),
            ClaimSeed("failed3", "FAILED", 3, false),
            ClaimSeed("retrying", "RETRYING", 0, false),
            ClaimSeed("published", "PUBLISHED", 0, true),
            ClaimSeed("pendingDone", "PENDING", 0, true),
            ClaimSeed("notListed", "PENDING", 0, false)
        )
        private const val LEGACY_CLAIM_JPQL = """
            UPDATE OutboxEvent o
            SET o.status = :nextStatus,
                o.updatedAt = :updatedAt
            WHERE o.id IN :ids
            AND o.processed = false
            AND (
                o.status = :pendingStatus
                OR (o.status = :failedStatus AND o.retryCount < :maxRetries)
            )
        """

        private val mysql: MySQLContainer<*> by lazy {
            val container: MySQLContainer<*> = MySQLContainer("mysql:8.0.44")
            container.withUsername("root")
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
