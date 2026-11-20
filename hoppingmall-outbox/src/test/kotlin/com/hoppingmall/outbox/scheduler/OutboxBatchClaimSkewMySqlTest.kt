package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import com.zaxxer.hikari.HikariDataSource
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.math.abs

@DisplayName("아웃박스 일괄 선점 두 발행기 몫 쏠림 진단")
@DisplayNameGeneration(ReplaceUnderscores::class)
@EnabledIfEnvironmentVariable(named = "OUTBOX_SKEW", matches = "true")
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [OutboxBatchClaimSkewMySqlTest.TestConfig::class])
class OutboxBatchClaimSkewMySqlTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

    private val sendCounts = ConcurrentHashMap<String, AtomicInteger>()

    private enum class StartMode(val label: String) { LATCH("latch"), PLAIN("plain") }

    private class Batch(val startNs: Long, val endNs: Long, val count: Int, val pending: Long?, val locks: String?)

    private class Op(val name: String, val startNs: Long, val endNs: Long)

    private class Track {
        var threadStartNs = 0L
        var goNs = 0L
        var endNs = 0L
        val batches = mutableListOf<Batch>()
        val ops = java.util.Collections.synchronizedList(mutableListOf<Op>())
    }

    private class RunResult(val mode: StartMode, val skewed: Boolean, val verdict: Int, val onceOk: Boolean, val error: Boolean)

    @Test
    fun 두_발행기의_배치별_시각과_잠근_행_수를_출발_방식별로_기록한다() {
        assumeTrue(PART == "all" || PART == "skew")
        println("SKEW_CONFIG runs=$RUNS rows=$ROWS gap=$SKEW_GAP order=$ORDER stats=$STATS lockSql=$LOCK_SQL")
        if (STATS == "empty") jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = 0")
        val results = mutableListOf<RunResult>()
        var seq = 0
        try {
            schedule().forEach { (mode, run) ->
                seq += 1
                results += runOnce(mode, run, seq)
            }
        } finally {
            jdbcTemplate.update("DELETE FROM outbox_events")
            jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = DEFAULT")
        }

        StartMode.entries.forEach { mode ->
            val mine = results.filter { it.mode == mode }
            println(
                "SKEW_SUMMARY mode=${mode.label} runs=${mine.size} skewed=${mine.count { it.skewed }} " +
                    "v1=${mine.count { it.verdict == 1 }} v2=${mine.count { it.verdict == 2 }} " +
                    "v9=${mine.count { it.verdict == 9 }} onceBad=${mine.count { !it.onceOk }} " +
                    "errors=${mine.count { it.error }}"
            )
        }
        assertThat(results.filter { !it.onceOk || it.error }).isEmpty()
    }

    private fun schedule(): List<Pair<StartMode, Int>> =
        if (ORDER == "sequential") {
            StartMode.entries.flatMap { mode -> (1..RUNS).map { mode to it } }
        } else {
            (1..RUNS).flatMap { run ->
                val pair = if (run % 2 == 1) listOf(StartMode.LATCH, StartMode.PLAIN)
                else listOf(StartMode.PLAIN, StartMode.LATCH)
                pair.map { it to run }
            }
        }

    @Test
    fun 통계가_빈_표에서_잠금_조회가_잡는_행_잠금_수를_FORCE_INDEX_유무별로_센다() {
        assumeTrue(PART == "all" || PART == "locks")
        jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = 0")
        try {
            val rows = listOf("empty", "analyzed").flatMap { stats ->
                jdbcTemplate.update("DELETE FROM outbox_events")
                if (stats == "empty") analyze()
                insertPending(ROWS)
                if (stats == "analyzed") analyze()
                listOf("plain" to LOCK_SQL_PLAIN, "force" to LOCK_SQL).map { (label, sql) ->
                    countLocks(stats, label, sql)
                }
            }
            rows.forEach { println(it) }
            assertThat(rows.filter { it.contains("query=force") }).allMatch { it.contains("primary=100 ") }
        } finally {
            jdbcTemplate.update("DELETE FROM outbox_events")
            jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = DEFAULT")
        }
    }

    @Test
    fun 앞_트랜잭션이_선점_단계마다_행을_쥔_동안_두_번째_잠금_조회가_받는_행_수를_센다() {
        assumeTrue(PART == "all" || PART == "second")
        jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = 0")
        try {
            val lines = listOf("empty", "analyzed").flatMap { stats ->
                SCENARIOS.flatMap { scenario ->
                    listOf("plain" to LOCK_SQL_PLAIN, "force" to LOCK_SQL).map { (label, sql) ->
                        secondReader(stats, scenario, label, sql)
                    }
                }
            }
            lines.forEach { println(it) }
        } finally {
            jdbcTemplate.update("DELETE FROM outbox_events")
            jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = DEFAULT")
        }
    }

    @Test
    fun 통계_상태별로_선점_UPDATE의_실행_계획이_PRIMARY_range인지_본다() {
        assumeTrue(PART == "all" || PART == "claimplan")
        try {
            val plans = listOf("empty", "natural", "analyzed").map { stats ->
                jdbcTemplate.update("DELETE FROM outbox_events")
                if (stats == "empty") {
                    jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = 0")
                    analyze()
                } else {
                    jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = DEFAULT")
                }
                insertPending(ROWS)
                if (stats == "analyzed") analyze()
                val ids = jdbcTemplate.queryForList(
                    "SELECT id FROM outbox_events ORDER BY id LIMIT $BATCH_LIMIT",
                    Long::class.java
                )
                ClaimPlan(stats, tableRows(), explain(claimSql(ids)), explain(claimSql(ids, CLAIM_SQL_PLAIN)))
            }
            plans.forEach {
                println("CLAIM_PLAN stats=${it.stats} stat=${it.statRows} force=${it.force} plain=${it.plain}")
            }
            assertThat(plans).allMatch { it.force.startsWith("PRIMARY|range|") }
        } finally {
            jdbcTemplate.update("DELETE FROM outbox_events")
            jdbcTemplate.execute("ALTER TABLE outbox_events STATS_AUTO_RECALC = DEFAULT")
        }
    }

    private class ClaimPlan(val stats: String, val statRows: String, val force: String, val plain: String)

    private fun secondReader(stats: String, scenario: String, label: String, sql: String): String {
        jdbcTemplate.update("DELETE FROM outbox_events")
        if (stats == "empty") analyze()
        insertPending(ROWS)
        if (stats == "analyzed") analyze()
        val statRows = tableRows()
        val held = mutableListOf<Connection>()
        try {
            when (scenario) {
                "lock" -> held += openTx().also { lockIds(it, sql) }
                "claim" -> held += openTx().also { claim(it, lockIds(it, sql)) }
                "claimCommitted" -> committed { claim(it, lockIds(it, sql)) }
                "mark" -> {
                    val ids = committed { lockIds(it, sql).also { ids -> claim(it, ids) } }
                    held += openTx().also { mark(it, ids) }
                }
                "markAfterOwnDone" -> {
                    val own = committed { lockIds(it, sql).also { ids -> claim(it, ids) } }
                    val other = committed { lockIds(it, sql).also { ids -> claim(it, ids) } }
                    committed { mark(it, own) }
                    held += openTx().also { mark(it, other) }
                }
                "claimLikeApp" -> held += openTx().also { claimLikeApp(it, lockIds(it, sql)) }
                "markLikeApp" -> {
                    val ids = committed { lockIds(it, sql).also { ids -> claimLikeApp(it, ids) } }
                    held += openTx().also { markLikeApp(it, ids) }
                }
            }
            val heldLocks = recordLockCount()
            resetStatementHistory()
            val reader = openTx().also { held += it }
            val started = System.nanoTime()
            val got = lockIds(reader, sql)
            val elapsed = ms(System.nanoTime() - started)
            return "SECOND stats=$stats stat=$statRows scenario=$scenario query=$label got=${got.size} " +
                "firstId=${got.minOrNull()} ms=$elapsed heldLocks=$heldLocks sql=${statementList()} " +
                "claimExplain=${explain(claimSql(listOf(1L, 2L, 3L)))} markExplain=${explain(markSql(listOf(1L, 2L, 3L)))}"
        } finally {
            held.forEach {
                it.rollback()
                it.close()
            }
        }
    }

    private fun openTx(): Connection =
        dataSource.connection.apply {
            autoCommit = false
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        }

    private fun <T> committed(block: (Connection) -> T): T =
        openTx().use { connection ->
            block(connection).also { connection.commit() }
        }

    private fun lockIds(connection: Connection, sql: String): List<Long> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.getLong(1)) }
            }
        }

    private fun claim(connection: Connection, ids: List<Long>) {
        if (ids.isEmpty()) return
        connection.createStatement().use {
            it.executeUpdate(
                "UPDATE outbox_events SET status = 'RETRYING', updated_at = NOW(6) WHERE id IN (${ids.joinToString(",")})"
            )
        }
    }

    private fun claimSql(ids: List<Long>, template: String = CLAIM_SQL): String =
        template
            .replace(":ids", ids.joinToString(","))
            .replace(":nextStatus", "'RETRYING'")
            .replace(":updatedAt", "'$CLAIM_AT'")
            .replace(":pendingStatus", "'PENDING'")
            .replace(":failedStatus", "'FAILED'")
            .replace(":maxRetries", "3")

    private fun markSql(ids: List<Long>): String =
        MARK_SQL
            .replace(":ids", ids.joinToString(","))
            .replace(":publishedStatus", "'PUBLISHED'")
            .replace(":processedAt", "NOW(6)")
            .replace(":updatedAt", "NOW(6)")
            .replace(":claimedStatus", "'RETRYING'")
            .replace(":claimedAt", "'$CLAIM_AT'")

    private fun claimLikeApp(connection: Connection, ids: List<Long>) {
        if (ids.isEmpty()) return
        connection.createStatement().use { it.executeUpdate(claimSql(ids)) }
    }

    private fun markLikeApp(connection: Connection, ids: List<Long>) {
        if (ids.isEmpty()) return
        connection.createStatement().use { it.executeUpdate(markSql(ids)) }
    }

    private fun recordLockCount(): String =
        jdbcTemplate.queryForList(
            "SELECT INDEX_NAME, COUNT(*) AS N FROM performance_schema.data_locks " +
                "WHERE OBJECT_SCHEMA = DATABASE() AND OBJECT_NAME = 'outbox_events' AND LOCK_TYPE = 'RECORD' " +
                "GROUP BY INDEX_NAME ORDER BY INDEX_NAME"
        ).joinToString(",", "[", "]") { "${it["INDEX_NAME"]}=${it["N"]}" }

    private fun mark(connection: Connection, ids: List<Long>) {
        if (ids.isEmpty()) return
        connection.createStatement().use {
            it.executeUpdate(
                "UPDATE outbox_events SET processed = true, status = 'PUBLISHED', processed_at = NOW(6), " +
                    "updated_at = NOW(6) WHERE id IN (${ids.joinToString(",")})"
            )
        }
    }

    private fun countLocks(stats: String, label: String, sql: String): String {
        val explain = explain(sql)
        val statRows = tableRows()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            try {
                val locked = connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rs -> generateSequence { if (rs.next()) 1 else null }.count() }
                }
                val locks = jdbcTemplate.queryForList(
                    "SELECT INDEX_NAME, LOCK_MODE, COUNT(*) AS N FROM performance_schema.data_locks " +
                        "WHERE OBJECT_SCHEMA = DATABASE() AND OBJECT_NAME = 'outbox_events' AND LOCK_TYPE = 'RECORD' " +
                        "GROUP BY INDEX_NAME, LOCK_MODE ORDER BY INDEX_NAME, LOCK_MODE"
                )
                val primary = locks.filter { it["INDEX_NAME"] == "PRIMARY" }.sumOf { (it["N"] as Number).toLong() }
                val secondary = locks.filter { it["INDEX_NAME"] != "PRIMARY" }.sumOf { (it["N"] as Number).toLong() }
                val detail = locks.joinToString(",", "[", "]") { "${it["INDEX_NAME"]}/${it["LOCK_MODE"]}=${it["N"]}" }
                return "LOCKS stats=$stats stat=$statRows query=$label returned=$locked primary=$primary " +
                    "secondary=$secondary total=${primary + secondary} detail=$detail explain=$explain"
            } finally {
                connection.rollback()
            }
        }
    }

    private fun analyze() {
        jdbcTemplate.queryForList("ANALYZE TABLE outbox_events")
    }

    private fun runOnce(mode: StartMode, run: Int, seq: Int): RunResult {
        jdbcTemplate.update("DELETE FROM outbox_events")
        sendCounts.clear()
        if (STATS == "empty") analyze()
        insertPending(ROWS)
        val explain = explain(LOCK_SQL)
        val explainPlain = explain(LOCK_SQL_PLAIN)
        val firstIds = jdbcTemplate.queryForList("SELECT id FROM outbox_events ORDER BY id LIMIT $BATCH_LIMIT", Long::class.java)
        val claimPlan = explain(claimSql(firstIds))
        val markPlan = explain(markSql(firstIds))
        val stats = tableRows()
        val history = historyLength()
        val pool = poolState()
        resetStatementHistory()

        val tracks = listOf(Track(), Track())
        val kafkas = listOf(countingKafka(), countingKafka())
        val publishers = tracks.indices.map { publisher(recordingRepository(tracks[it]), kafkas[it].template) }
        val executor = Executors.newFixedThreadPool(publishers.size)
        val t0 = System.nanoTime()
        val futures = start(executor, publishers, tracks, waitReady = mode == StartMode.LATCH)
        val errors = futures.mapNotNull { future ->
            runCatching { future.get(60, TimeUnit.SECONDS) }.exceptionOrNull()
        }
        executor.shutdownNow()

        val shares = kafkas.map { it.sent.get() }
        val onceOk = sendCounts.size == ROWS &&
            sendCounts.values.all { it.get() == 1 } &&
            countByStatus("PUBLISHED") == ROWS.toLong()
        val skewed = abs(shares[0] - shares[1]) > SKEW_GAP
        val verdict = verdict(shares, tracks, skewed)
        val firstGap = tracks.map { it.batches.firstOrNull()?.startNs }
            .let { (a, b) -> if (a != null && b != null) ms(b - a) else "na" }

        println(
            "SKEW mode=${mode.label} run=$run seq=$seq shares=[${shares.joinToString(",")}] " +
                "once=${if (onceOk) "ok" else "bad"} pool=$pool stat=$stats hist=$history explain=$explain " +
                "explainPlain=$explainPlain claimPlan=$claimPlan markPlan=$markPlan " +
                tracks.mapIndexed { i, t -> "p${i}ops=${opList(t, t0)}" }.joinToString(" ") + " " +
                tracks.mapIndexed { i, t -> "p${i}zero=${t.batches.mapNotNull { it.locks }.joinToString(";")}" }
                    .joinToString(" ") + " " +
                tracks.mapIndexed { i, t -> "p${i}t=${timeline(t, t0)}" }.joinToString(" ") + " " +
                tracks.mapIndexed { i, t -> "p$i=${batchList(t, t0)}" }.joinToString(" ") + " " +
                "firstGapMs=$firstGap sql=${statementList()} " +
                "error=${errors.firstOrNull()?.let { (it.cause ?: it).javaClass.simpleName } ?: "none"} " +
                "verdict=$verdict"
        )
        return RunResult(mode, skewed, verdict, onceOk, errors.isNotEmpty())
    }

    private fun start(
        executor: ExecutorService,
        publishers: List<OutboxEventPublisher>,
        tracks: List<Track>,
        waitReady: Boolean
    ): List<Future<*>> {
        val ready = CountDownLatch(publishers.size)
        val go = CountDownLatch(1)
        val futures = publishers.mapIndexed { i, publisher ->
            executor.submit(Runnable {
                val track = tracks[i]
                track.threadStartNs = System.nanoTime()
                ready.countDown()
                go.await()
                track.goNs = System.nanoTime()
                try {
                    publisher.publishPendingEvents()
                } finally {
                    track.endNs = System.nanoTime()
                }
            })
        }
        if (waitReady) check(ready.await(10, TimeUnit.SECONDS)) { "publisher threads did not start" }
        go.countDown()
        return futures
    }

    private fun verdict(shares: List<Int>, tracks: List<Track>, skewed: Boolean): Int {
        if (!skewed) return 0
        val winner = if (shares[0] >= shares[1]) 0 else 1
        val w = tracks[winner]
        val l = tracks[1 - winner]
        val loserFirst = l.batches.firstOrNull()?.startNs ?: return 1
        val winnerLastClaimEnd = w.batches.filter { it.count > 0 }.maxOfOrNull { it.endNs } ?: return 9
        if (loserFirst >= winnerLastClaimEnd) return 1
        val loserStop = l.batches.last()
        val winnerContinued = w.batches.any { it.count > 0 && it.startNs > loserStop.startNs }
        return if (loserStop.count < BATCH_LIMIT && winnerContinued) 2 else 9
    }

    private fun recordingRepository(track: Track): OutboxEventRepository {
        val handler = InvocationHandler { _, method, args ->
            if (method.name in TRACED_OPS) {
                val opStart = System.nanoTime()
                return@InvocationHandler delegate(method, args).also {
                    track.ops += Op(TRACED_OPS.getValue(method.name), opStart, System.nanoTime())
                }
            }
            if (method.name != "lockUnprocessedEventIds") return@InvocationHandler delegate(method, args)
            val startNs = System.nanoTime()
            @Suppress("UNCHECKED_CAST")
            val ids = delegate(method, args) as List<Long>
            val endNs = System.nanoTime()
            val pending = if (ids.size < BATCH_LIMIT) pendingCount() else null
            val locks = if (pending != null && pending > 0) lockSnapshot() else null
            track.batches += Batch(startNs, endNs, ids.size, pending, locks)
            ids
        }
        return Proxy.newProxyInstance(
            OutboxEventRepository::class.java.classLoader,
            arrayOf(OutboxEventRepository::class.java),
            handler
        ) as OutboxEventRepository
    }

    private fun delegate(method: Method, args: Array<out Any?>?): Any? =
        try {
            method.invoke(outboxEventRepository, *(args ?: emptyArray<Any?>()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

    private fun publisher(repository: OutboxEventRepository, kafkaTemplate: KafkaTemplate<String, Any>): OutboxEventPublisher {
        val converter = Mockito.mock(AvroEventConverter::class.java)
        whenever(converter.convertJsonToAvro(any(), any())).thenReturn(Mockito.mock(GenericRecord::class.java))
        return OutboxEventPublisher(
            repository,
            kafkaTemplate,
            Mockito.mock(OutboxMetrics::class.java),
            converter,
            transactionManager
        )
    }

    private fun opList(track: Track, t0: Long): String =
        synchronized(track.ops) {
            track.ops.joinToString(",", "[", "]") { "${it.name}${ms(it.startNs - t0)}-${ms(it.endNs - t0)}" }
        }

    private fun lockSnapshot(): String =
        runCatching {
            val locks = jdbcTemplate.queryForList(
                "SELECT THREAD_ID, INDEX_NAME, LOCK_MODE, COUNT(*) AS N FROM performance_schema.data_locks " +
                    "WHERE OBJECT_SCHEMA = DATABASE() AND OBJECT_NAME = 'outbox_events' AND LOCK_TYPE = 'RECORD' " +
                    "GROUP BY THREAD_ID, INDEX_NAME, LOCK_MODE"
            ).joinToString("|") { "t${it["THREAD_ID"]}:${it["INDEX_NAME"]}:${it["LOCK_MODE"].toString().replace(',', '+')}=${it["N"]}" }
            val running = jdbcTemplate.queryForList(
                "SELECT THREAD_ID, LEFT(SQL_TEXT, 40) AS T FROM performance_schema.events_statements_current " +
                    "WHERE SQL_TEXT LIKE '%outbox_events%' AND SQL_TEXT NOT LIKE '%performance_schema%'"
            ).joinToString("|") { "t${it["THREAD_ID"]}:${it["T"].toString().replace(' ', '_')}" }
            "locks{$locks}running{$running}"
        }.getOrElse { "na" }

    private fun timeline(track: Track, t0: Long): String =
        "[start=${ms(track.threadStartNs - t0)},go=${ms(track.goNs - t0)}," +
            "first=${track.batches.firstOrNull()?.let { ms(it.startNs - t0) } ?: "na"},end=${ms(track.endNs - t0)}]"

    private fun batchList(track: Track, t0: Long): String =
        track.batches.joinToString(",", "[", "]") { batch ->
            val base = "${ms(batch.startNs - t0)}:${batch.count}"
            batch.pending?.let { "$base:pend$it" } ?: base
        }

    private fun ms(ns: Long): String = String.format(Locale.ROOT, "%.1f", ns / 1_000_000.0)

    private fun pendingCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE processed = false AND status = 'PENDING'",
            Long::class.java
        )!!

    private fun explain(sql: String): String =
        runCatching {
            jdbcTemplate.queryForList("EXPLAIN $sql").joinToString(";") { row ->
                listOf(row["key"], row["type"], row["rows"], row["Extra"])
                    .joinToString("|") { it.toString().replace(' ', '_') }
            }
        }.getOrElse { "na" }

    private fun tableRows(): String =
        runCatching {
            jdbcTemplate.queryForList(
                "SELECT n_rows FROM mysql.innodb_table_stats WHERE database_name = DATABASE() AND table_name = 'outbox_events'",
                Long::class.java
            ).firstOrNull()?.toString() ?: "na"
        }.getOrElse { "na" }

    private fun historyLength(): String =
        runCatching {
            jdbcTemplate.queryForList(
                "SELECT COUNT FROM information_schema.INNODB_METRICS WHERE NAME = 'trx_rseg_history_len'",
                Long::class.java
            ).firstOrNull()?.toString() ?: "na"
        }.getOrElse { "na" }

    private fun poolState(): String =
        runCatching {
            val pool = dataSource.unwrap(HikariDataSource::class.java).hikariPoolMXBean
            "${pool.totalConnections}/${pool.idleConnections}"
        }.getOrElse { "na" }

    private fun resetStatementHistory() {
        jdbcTemplate.update(
            "UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' WHERE NAME = 'events_statements_history_long'"
        )
        jdbcTemplate.update("TRUNCATE TABLE performance_schema.events_statements_history_long")
    }

    private fun statementList(): String =
        jdbcTemplate.queryForList(
            "SELECT THREAD_ID, ROWS_EXAMINED, ROWS_SENT, SELECT_SCAN, SELECT_RANGE, SORT_ROWS, NO_INDEX_USED, SQL_TEXT " +
                "FROM performance_schema.events_statements_history_long " +
                "WHERE CURRENT_SCHEMA = DATABASE() AND SQL_TEXT LIKE '%SKIP LOCKED%' AND SQL_TEXT NOT LIKE '%EXPLAIN%' " +
                "ORDER BY TIMER_START"
        ).joinToString(",", "[", "]") { row ->
            val flags = buildString {
                if ((row["SELECT_SCAN"] as Number).toLong() > 0) append('s')
                if ((row["SELECT_RANGE"] as Number).toLong() > 0) append('r')
                if ((row["SORT_ROWS"] as Number).toLong() > 0) append('f')
                if ((row["NO_INDEX_USED"] as Number).toLong() > 0) append('n')
                if ((row["SQL_TEXT"] as String).contains("FORCE INDEX")) append('x')
            }
            "t${row["THREAD_ID"]}:${row["ROWS_EXAMINED"]}/${row["ROWS_SENT"]}$flags"
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

    private class CountingKafka(val template: KafkaTemplate<String, Any>, val sent: AtomicInteger)

    @Suppress("UNCHECKED_CAST")
    private fun countingKafka(): CountingKafka {
        val template = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
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
                (invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>).doInOperations(template)
            }
        return CountingKafka(template, sent)
    }

    @Configuration
    @EntityScan(basePackageClasses = [OutboxEvent::class])
    @EnableJpaRepositories(basePackageClasses = [OutboxEventRepository::class])
    class TestConfig

    companion object {
        private const val BATCH_LIMIT = 100
        private val LOCK_SQL: String = OutboxEventRepository::class.java.methods
            .first { it.name == "lockUnprocessedEventIds" }
            .getAnnotation(Query::class.java).value
            .replace(":retryStatus", "'FAILED'")
            .replace(":status", "'PENDING'")
            .replace(":maxRetries", "3")
            .replace(":limit", BATCH_LIMIT.toString())
            .replace(Regex("\\s+"), " ")
            .trim()
        private val MARK_SQL: String = OutboxEventRepository::class.java.methods
            .first { it.name == "markClaimedEventsProcessed" }
            .getAnnotation(Query::class.java).value
            .replace(Regex("\\s+"), " ")
            .trim()
        private val LOCK_SQL_PLAIN: String = LOCK_SQL.replace(Regex("FORCE INDEX \\([^)]*\\) "), "")
        private val CLAIM_SQL: String = OutboxEventRepository::class.java.methods
            .first { it.name == "claimEventsForPublish" }
            .getAnnotation(Query::class.java).value
            .replace(Regex("\\s+"), " ")
            .trim()
        private val CLAIM_SQL_PLAIN: String = CLAIM_SQL.replace(Regex("FORCE INDEX \\([^)]*\\) "), "")

        private fun env(name: String, default: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

        private val RUNS = env("OUTBOX_SKEW_RUNS", "20").toInt()
        private val ROWS = env("OUTBOX_SKEW_ROWS", "1000").toInt()
        private val SKEW_GAP = env("OUTBOX_SKEW_GAP", "100").toInt()
        private val ORDER = env("OUTBOX_SKEW_ORDER", "interleave")
        private val STATS = env("OUTBOX_SKEW_STATS", "natural")
        private val PART = env("OUTBOX_SKEW_PART", "all")
        private val SCENARIOS = env("OUTBOX_SKEW_SCENARIOS", "lock,claimLikeApp,markLikeApp")
            .split(",")
        private const val CLAIM_AT = "2026-11-20 00:00:00.123456"
        private val TRACED_OPS = mapOf(
            "claimEventsForPublish" to "C",
            "findAllById" to "R",
            "markClaimedEventsProcessed" to "M"
        )

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
