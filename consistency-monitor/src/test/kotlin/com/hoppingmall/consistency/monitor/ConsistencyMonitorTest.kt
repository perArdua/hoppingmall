package com.hoppingmall.consistency.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.hoppingmall.consistency.check.CheckResult
import com.hoppingmall.consistency.check.InvariantChecks
import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.config.InvariantPolicy
import com.hoppingmall.consistency.enums.Severity
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.invariant.InvariantStatement
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("ConsistencyMonitor")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ConsistencyMonitorTest {

    private val contradiction = definition("INV-1")
    private val progress = definition("INV-2b")
    private val crossSource = definition("INV-3b")
    private val pair = definition("INV-1R")
    private val properties = ConsistencyProperties(
        policies = mapOf(
            "INV-1" to InvariantPolicy(severity = Severity.CRITICAL, graceSeconds = Duration.ZERO),
            "INV-1R" to InvariantPolicy(severity = Severity.CRITICAL, graceSeconds = INTERVAL),
            "INV-2b" to InvariantPolicy(severity = Severity.WARN, graceSeconds = Duration.ofSeconds(60)),
            "INV-3b" to InvariantPolicy(severity = Severity.WARN, graceSeconds = Duration.ofSeconds(60))
        )
    )
    private val now = LocalDateTime.of(2026, 10, 22, 19, 30, 0)

    private val catalog: InvariantCatalog = mock()
    private val checks: InvariantChecks = mock()
    private val repository: ViolationRepository = mock()
    private val lease: MonitorLease = mock()
    private val registry = SimpleMeterRegistry()
    private lateinit var monitor: ConsistencyMonitor

    @BeforeEach
    fun setUp() {
        whenever(catalog.definitions).thenReturn(listOf(contradiction, progress, crossSource, pair))
        whenever(repository.now()).thenReturn(now)
        whenever(repository.findOpen(any())).thenReturn(emptyList())
        whenever(checks.recent(any(), any())).thenReturn(CheckResult.complete(emptyList()))
        whenever(checks.isSweepable(crossSource)).thenReturn(true)
        whenever(lease.tryAcquire()).thenReturn(true)
        monitor = monitor(lease)
    }

    private fun monitor(lease: MonitorLease?) = ConsistencyMonitor(
        catalog,
        properties,
        checks,
        repository,
        InvariantMetrics(registry, catalog, properties),
        ObjectMapper().registerModule(JavaTimeModule()),
        lease
    )

    private fun definition(id: String) = InvariantDefinition(id, id, listOf(InvariantStatement("check", "order", "SELECT 1")))

    private fun gauge(name: String, inv: String): Double = registry.get(name).tag("inv", inv).gauge().value()

    private fun open(invId: String, key: String, firstSeen: LocalDateTime, confirmedAt: LocalDateTime? = null, id: Long = 1L) =
        Violation(id, invId, key, "WARN", firstSeen, firstSeen, confirmedAt, null, null, null, "{}")

    private fun rows(vararg keys: Any): CheckResult = CheckResult.complete(keys.map { mapOf("key" to it) })

    @Test
    fun 유예가_0인_항목은_처음_관측된_주기에_확정_위반으로_센다() {
        whenever(checks.recent(eq(contradiction), any()))
            .thenReturn(CheckResult.complete(listOf(mapOf("order_id" to 10L, "status" to "CANCELLED"))))

        monitor.runCycle()

        verify(repository).insert(eq("INV-1"), eq("10"), eq("CRITICAL"), eq(now), eq(now), any())
        assertThat(gauge("invariant.violation.count", "INV-1")).isEqualTo(1.0)
        assertThat(gauge("invariant.violation.candidates", "INV-1")).isEqualTo(0.0)
    }

    @Test
    fun 유예_안의_진행형_후보는_확정하지_않고_후보로만_센다() {
        whenever(checks.recent(eq(progress), any())).thenReturn(rows(7L))

        monitor.runCycle()

        verify(repository).insert(eq("INV-2b"), eq("7"), eq("WARN"), eq(now), isNull(), any())
        assertThat(gauge("invariant.violation.count", "INV-2b")).isEqualTo(0.0)
        assertThat(gauge("invariant.violation.candidates", "INV-2b")).isEqualTo(1.0)
    }

    @Test
    fun 유예를_넘겨_계속_관측된_후보는_확정하고_경과_시간을_기록한다() {
        whenever(checks.recent(eq(progress), any())).thenReturn(rows(7L))
        whenever(repository.findOpen("INV-2b")).thenReturn(listOf(open("INV-2b", "7", now.minusSeconds(90))))

        monitor.runCycle()

        verify(repository).touch(eq(1L), eq(now), eq(now), any())
        assertThat(gauge("invariant.violation.count", "INV-2b")).isEqualTo(1.0)
        assertThat(gauge("invariant.violation.age.seconds.max", "INV-2b")).isEqualTo(90.0)
    }

    @Test
    fun 이미_확정된_위반은_계속_확정으로_센다() {
        whenever(checks.recent(eq(progress), any())).thenReturn(rows(7L))
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
    fun DB를_걸치는_점검은_열린_위반의_키를_넘기고_다시_확인한_키만_해소한다() {
        val rechecked = open("INV-3b", "r-1", now.minusSeconds(90), id = 1L)
        val notChecked = open("INV-3b", "r-2", now.minusSeconds(90), id = 2L)
        whenever(repository.findOpen("INV-3b")).thenReturn(listOf(rechecked, notChecked))
        var passedKeys: Collection<String> = emptyList()
        whenever(checks.recent(eq(crossSource), any())).doAnswer { invocation ->
            passedKeys = invocation.getArgument<() -> Collection<String>>(1).invoke()
            CheckResult(emptyList()) { it == "r-1" }
        }

        monitor.runCycle()

        assertThat(passedKeys).containsExactlyInAnyOrder("r-1", "r-2")
        verify(repository).resolve(1L, now, ConsistencyMonitor.SYSTEM_ACTOR, ConsistencyMonitor.AUTO_RESOLUTION_NOTE)
        verify(repository, never()).resolve(eq(2L), any(), any(), any())
    }

    @Test
    fun 다시_확인한_열린_위반이_계속_관측되면_유예를_넘긴_뒤_확정한다() {
        whenever(repository.findOpen("INV-3b")).thenReturn(listOf(open("INV-3b", "r-1", now.minusSeconds(61))))
        whenever(checks.recent(eq(crossSource), any()))
            .thenReturn(CheckResult(listOf(mapOf("reservation_id" to "r-1"))) { true })

        monitor.runCycle()

        verify(repository).touch(eq(1L), eq(now), eq(now), any())
        assertThat(gauge("invariant.violation.count", "INV-3b")).isEqualTo(1.0)
    }

    @Test
    fun 유예가_한_주기인_어긋남이_다음_주기에_사라지면_확정하지_않고_해소한다() {
        val nextCycle = now.plus(INTERVAL)
        whenever(repository.now()).thenReturn(now, nextCycle)
        whenever(repository.findOpen("INV-1R")).thenReturn(emptyList(), listOf(open("INV-1R", "20", now)))
        whenever(checks.recent(eq(pair), any())).thenReturn(rows(20L), CheckResult.complete(emptyList()))

        monitor.runCycle()
        monitor.runCycle()

        verify(repository).insert(eq("INV-1R"), eq("20"), eq("CRITICAL"), eq(now), isNull(), any())
        verify(repository, never()).touch(any(), any(), anyOrNull(), any())
        verify(repository).resolve(1L, nextCycle, ConsistencyMonitor.SYSTEM_ACTOR, ConsistencyMonitor.AUTO_RESOLUTION_NOTE)
        assertThat(gauge("invariant.violation.count", "INV-1R")).isEqualTo(0.0)
    }

    @Test
    fun 유예가_한_주기인_어긋남이_두_번째_주기에도_남으면_확정한다() {
        val nextCycle = now.plus(INTERVAL)
        whenever(repository.now()).thenReturn(now, nextCycle)
        whenever(repository.findOpen("INV-1R")).thenReturn(emptyList(), listOf(open("INV-1R", "20", now)))
        whenever(checks.recent(eq(pair), any())).thenReturn(rows(20L), rows(20L))

        monitor.runCycle()
        assertThat(gauge("invariant.violation.count", "INV-1R")).isEqualTo(0.0)
        assertThat(gauge("invariant.violation.candidates", "INV-1R")).isEqualTo(1.0)
        monitor.runCycle()

        verify(repository).insert(eq("INV-1R"), eq("20"), eq("CRITICAL"), eq(now), isNull(), any())
        verify(repository).touch(eq(1L), eq(nextCycle), eq(nextCycle), any())
        verify(repository, never()).resolve(any(), any(), any(), any())
        assertThat(gauge("invariant.violation.count", "INV-1R")).isEqualTo(1.0)
        assertThat(gauge("invariant.violation.age.seconds.max", "INV-1R")).isEqualTo(INTERVAL.seconds.toDouble())
    }

    @Test
    fun 쿼리가_실패하면_오류를_세고_다른_항목은_계속_검사한다() {
        whenever(checks.recent(eq(contradiction), any())).thenThrow(DataAccessResourceFailureException("down"))
        whenever(checks.recent(eq(progress), any())).thenReturn(rows(7L))

        monitor.runCycle()

        assertThat(registry.get("invariant.check.errors").tag("inv", "INV-1").counter().count()).isEqualTo(1.0)
        verify(repository, never()).findOpen("INV-1")
        verify(repository).insert(eq("INV-2b"), eq("7"), any(), any(), anyOrNull(), any())
        assertThat(registry.get("invariant.check.cycle.duration").timer().count()).isEqualTo(1)
        assertThat(registry.get("invariant.check.duration").tag("inv", "INV-1").timer().count()).isEqualTo(1)
    }

    @Test
    fun 임대를_얻지_못하면_점검과_전체_훑기를_건너뛴다() {
        whenever(lease.tryAcquire()).thenReturn(false)

        monitor.runCycle()
        monitor.runFullSweep()

        verify(checks, never()).recent(any(), any())
        verify(checks, never()).sweep(any(), any())
    }

    @Test
    fun 전체_훑기는_DB를_걸치는_점검만_돌고_찾은_어긋남을_기록하되_해소하지_않는다() {
        whenever(repository.findOpen("INV-3b"))
            .thenReturn(listOf(open("INV-3b", "r-1", now.minusSeconds(90), id = 1L), open("INV-3b", "r-9", now, id = 9L)))
        whenever(checks.sweep(eq(crossSource), any())).doAnswer { invocation ->
            val onRows = invocation.getArgument<(List<Map<String, Any?>>) -> Unit>(1)
            onRows(listOf(mapOf("reservation_id" to "r-1")))
            onRows(listOf(mapOf("reservation_id" to "r-2")))
        }

        monitor(null).runFullSweep()

        verify(checks, never()).sweep(eq(contradiction), any())
        verify(repository).touch(eq(1L), eq(now), eq(now), any())
        verify(repository).insert(eq("INV-3b"), eq("r-2"), eq("WARN"), eq(now), isNull(), any())
        verify(repository, never()).resolve(any(), any(), any(), any())
        assertThat(registry.get("invariant.check.sweep.duration").timer().count()).isEqualTo(1)
    }

    @Test
    fun 전체_훑기가_실패하면_오류를_센다() {
        whenever(checks.sweep(eq(crossSource), any())).thenThrow(DataAccessResourceFailureException("down"))

        monitor.runFullSweep()

        assertThat(registry.get("invariant.check.errors").tag("inv", "INV-3b").counter().count()).isEqualTo(1.0)
        verify(repository, never()).insert(any(), any(), any(), any(), anyOrNull(), any())
    }

    companion object {
        private val INTERVAL: Duration = Duration.ofSeconds(60)
    }
}
