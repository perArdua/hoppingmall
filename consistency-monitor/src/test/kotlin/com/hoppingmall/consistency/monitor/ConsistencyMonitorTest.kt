package com.hoppingmall.consistency.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.config.InvariantPolicy
import com.hoppingmall.consistency.enums.Severity
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.violation.Violation
import com.hoppingmall.consistency.violation.ViolationRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

@DisplayName("ConsistencyMonitor")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ConsistencyMonitorTest {

    private val contradiction = InvariantDefinition("INV-1", "모순", "SELECT contradiction")
    private val progress = InvariantDefinition("INV-2b", "진행", "SELECT progress")
    private val properties = ConsistencyProperties(
        policies = mapOf(
            "INV-1" to InvariantPolicy(severity = Severity.CRITICAL, graceSeconds = 0),
            "INV-2b" to InvariantPolicy(severity = Severity.WARN, graceSeconds = 60)
        )
    )
    private val now = LocalDateTime.of(2026, 10, 22, 19, 30, 0)

    private val catalog: InvariantCatalog = mock()
    private val jdbcTemplate: JdbcTemplate = mock()
    private val repository: ViolationRepository = mock()
    private val registry = SimpleMeterRegistry()
    private lateinit var monitor: ConsistencyMonitor

    @BeforeEach
    fun setUp() {
        whenever(catalog.definitions).thenReturn(listOf(contradiction, progress))
        whenever(repository.now()).thenReturn(now)
        whenever(repository.findOpen(any())).thenReturn(emptyList())
        whenever(jdbcTemplate.queryForList(any<String>())).thenReturn(emptyList())
        monitor = ConsistencyMonitor(
            catalog,
            properties,
            jdbcTemplate,
            repository,
            InvariantMetrics(registry, catalog, properties),
            ObjectMapper().registerModule(JavaTimeModule())
        )
    }

    private fun gauge(name: String, inv: String): Double = registry.get(name).tag("inv", inv).gauge().value()

    private fun open(invId: String, key: String, firstSeen: LocalDateTime, confirmedAt: LocalDateTime? = null) =
        Violation(1L, invId, key, "WARN", firstSeen, firstSeen, confirmedAt, null, null, null, "{}")

    @Test
    fun 유예가_0인_항목은_처음_관측된_주기에_확정_위반으로_센다() {
        whenever(jdbcTemplate.queryForList("SELECT contradiction"))
            .thenReturn(listOf(mapOf("order_id" to 10L, "status" to "CANCELLED")))

        monitor.runCycle()

        verify(repository).insert(eq("INV-1"), eq("10"), eq("CRITICAL"), eq(now), eq(now), any())
        assertThat(gauge("invariant.violation.count", "INV-1")).isEqualTo(1.0)
        assertThat(gauge("invariant.violation.candidates", "INV-1")).isEqualTo(0.0)
    }

    @Test
    fun 유예_안의_진행형_후보는_확정하지_않고_후보로만_센다() {
        whenever(jdbcTemplate.queryForList("SELECT progress")).thenReturn(listOf(mapOf("order_id" to 7L)))

        monitor.runCycle()

        verify(repository).insert(eq("INV-2b"), eq("7"), eq("WARN"), eq(now), isNull(), any())
        assertThat(gauge("invariant.violation.count", "INV-2b")).isEqualTo(0.0)
        assertThat(gauge("invariant.violation.candidates", "INV-2b")).isEqualTo(1.0)
    }

    @Test
    fun 유예를_넘겨_계속_관측된_후보는_확정하고_경과_시간을_기록한다() {
        whenever(jdbcTemplate.queryForList("SELECT progress")).thenReturn(listOf(mapOf("order_id" to 7L)))
        whenever(repository.findOpen("INV-2b")).thenReturn(listOf(open("INV-2b", "7", now.minusSeconds(90))))

        monitor.runCycle()

        verify(repository).touch(eq(1L), eq(now), eq(now), any())
        assertThat(gauge("invariant.violation.count", "INV-2b")).isEqualTo(1.0)
        assertThat(gauge("invariant.violation.age.seconds.max", "INV-2b")).isEqualTo(90.0)
    }

    @Test
    fun 이미_확정된_위반은_계속_확정으로_센다() {
        whenever(jdbcTemplate.queryForList("SELECT progress")).thenReturn(listOf(mapOf("order_id" to 7L)))
        whenever(repository.findOpen("INV-2b"))
            .thenReturn(listOf(open("INV-2b", "7", now.minusSeconds(30), confirmedAt = now.minusSeconds(10))))

        monitor.runCycle()

        verify(repository).touch(eq(1L), eq(now), isNull(), any())
        assertThat(gauge("invariant.violation.count", "INV-2b")).isEqualTo(1.0)
    }

    @Test
    fun 더_이상_관측되지_않는_열린_위반은_자동으로_해소한다() {
        whenever(repository.findOpen("INV-1")).thenReturn(listOf(open("INV-1", "10", now.minusSeconds(60), now)))

        monitor.runCycle()

        verify(repository).resolve(1L, now, ConsistencyMonitor.SYSTEM_ACTOR, ConsistencyMonitor.AUTO_RESOLUTION_NOTE)
        assertThat(gauge("invariant.violation.count", "INV-1")).isEqualTo(0.0)
    }

    @Test
    fun 쿼리가_실패하면_오류를_세고_다른_항목은_계속_검사한다() {
        whenever(jdbcTemplate.queryForList("SELECT contradiction")).thenThrow(DataAccessResourceFailureException("down"))
        whenever(jdbcTemplate.queryForList("SELECT progress")).thenReturn(listOf(mapOf("order_id" to 7L)))

        monitor.runCycle()

        assertThat(registry.get("invariant.check.errors").tag("inv", "INV-1").counter().count()).isEqualTo(1.0)
        verify(repository, never()).findOpen("INV-1")
        verify(repository).insert(eq("INV-2b"), eq("7"), any(), any(), anyOrNull(), any())
        assertThat(registry.get("invariant.check.cycle.duration").timer().count()).isEqualTo(1)
        assertThat(registry.get("invariant.check.duration").tag("inv", "INV-1").timer().count()).isEqualTo(1)
    }
}
