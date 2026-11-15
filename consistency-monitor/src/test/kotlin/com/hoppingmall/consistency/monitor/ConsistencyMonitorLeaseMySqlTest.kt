package com.hoppingmall.consistency.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.support.MySqlContainerSupport
import com.hoppingmall.consistency.violation.ViolationRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@DisplayName("ConsistencyMonitor 인스턴스 사이 점검 선점")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ConsistencyMonitorLeaseMySqlTest {

    private val dataSource = DriverManagerDataSource(
        MySqlContainerSupport.jdbcUrl("consistency_lease"),
        MySqlContainerSupport.username,
        MySqlContainerSupport.password
    )
    private val jdbcTemplate = JdbcTemplate(dataSource)
    private val executor = Executors.newFixedThreadPool(2)
    private val properties = ConsistencyProperties()
    private val catalog: InvariantCatalog = mock()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS invariant_violations")
        jdbcTemplate.execute("DROP TABLE IF EXISTS monitor_leases")
        jdbcTemplate.execute("DROP TABLE IF EXISTS lease_probe")
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)
        jdbcTemplate.execute("CREATE TABLE lease_probe (id BIGINT PRIMARY KEY)")
        jdbcTemplate.update("INSERT INTO lease_probe (id) VALUES (1)")
        whenever(catalog.definitions).thenReturn(listOf(BLOCKING))
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    private fun lease(owner: String) = MonitorLease(jdbcTemplate, LEASE_SECONDS, owner)

    private fun monitor(owner: String) = ConsistencyMonitor(
        catalog,
        properties,
        jdbcTemplate,
        ViolationRepository(jdbcTemplate),
        InvariantMetrics(SimpleMeterRegistry(), catalog, properties),
        ObjectMapper().registerModule(JavaTimeModule()),
        lease(owner)
    )

    private fun lockWaitCount(): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
            WHERE l.OBJECT_SCHEMA = DATABASE()
            AND l.OBJECT_NAME = 'lease_probe'
            """.trimIndent(),
            Long::class.java
        )!!

    private fun awaitLockWaits(expected: Long, pending: Future<*>) {
        val deadline = System.currentTimeMillis() + WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount() >= expected) return
            if (pending.isDone) throw AssertionError("첫 인스턴스의 점검이 잠금에 걸리지 않고 끝났다: ${pending.get()}")
            Thread.onSpinWait()
        }
        throw AssertionError("lease_probe 잠금 대기 ${expected}개가 ${WAIT_LIMIT_MS}ms 안에 보이지 않았다")
    }

    private fun finishedWithoutWaiting(pending: Future<*>): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (pending.isDone) return true
            if (lockWaitCount() >= 2) return false
            Thread.onSpinWait()
        }
        return false
    }

    private fun openViolations(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM invariant_violations WHERE inv_id = ? AND resolved_at IS NULL",
            Long::class.java,
            BLOCKING.id
        )!!

    @Test
    fun 다른_인스턴스가_점검_중이면_점검을_건너뛰고_같은_위반을_겹쳐_쓰지_않는다() {
        val first = monitor("monitor-a")
        val second = monitor("monitor-b")
        val holder = dataSource.connection
        val secondSkipped: Boolean
        try {
            holder.autoCommit = false
            holder.createStatement().use { it.executeQuery("SELECT id FROM lease_probe WHERE id = 1 FOR UPDATE") }
            val firstCycle = executor.submit { first.runCycle() }
            awaitLockWaits(1, firstCycle)
            val secondCycle = executor.submit { second.runCycle() }
            secondSkipped = finishedWithoutWaiting(secondCycle)
            holder.commit()
            firstCycle.get(WAIT_LIMIT_MS, TimeUnit.MILLISECONDS)
            secondCycle.get(WAIT_LIMIT_MS, TimeUnit.MILLISECONDS)
        } finally {
            holder.rollback()
            holder.close()
        }

        assertThat(secondSkipped).`as`("둘째 인스턴스는 첫 인스턴스의 점검을 기다리지 않고 건너뛴다").isTrue()
        assertThat(openViolations()).isEqualTo(1)
    }

    @Test
    fun 임대를_쥔_인스턴스가_있으면_다른_인스턴스는_선점하지_못하고_놓으면_이어받는다() {
        val first = lease("monitor-a")
        val second = lease("monitor-b")

        assertThat(first.tryAcquire()).isTrue()
        assertThat(first.tryAcquire()).isTrue()
        assertThat(second.tryAcquire()).isFalse()

        first.release()

        assertThat(second.tryAcquire()).isTrue()
        assertThat(first.tryAcquire()).isFalse()
    }

    @Test
    fun 임대를_쥔_인스턴스가_죽어_임대가_지나면_다른_인스턴스가_이어받는다() {
        val first = lease("monitor-a")
        val second = lease("monitor-b")
        assertThat(first.tryAcquire()).isTrue()
        assertThat(second.tryAcquire()).isFalse()

        jdbcTemplate.update("UPDATE monitor_leases SET lease_until = NOW(6) - INTERVAL 1 SECOND")

        assertThat(second.tryAcquire()).isTrue()
        assertThat(first.tryAcquire()).isFalse()
    }

    companion object {
        private val BLOCKING = InvariantDefinition(
            "INV-LEASE",
            "잠긴 행을 읽는 점검",
            "SELECT id AS entity_key FROM lease_probe WHERE id = 1 FOR UPDATE"
        )
        private const val LEASE_SECONDS = 300L
        private const val WAIT_LIMIT_MS = 15_000L
    }
}
