package com.hoppingmall.consistency.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.consistency.check.CheckResult
import com.hoppingmall.consistency.check.InvariantChecks
import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.config.InvariantPolicy
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.violation.Violation
import com.hoppingmall.consistency.violation.ViolationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class ConsistencyMonitor(
    private val catalog: InvariantCatalog,
    private val properties: ConsistencyProperties,
    private val checks: InvariantChecks,
    private val violationRepository: ViolationRepository,
    private val metrics: InvariantMetrics,
    private val objectMapper: ObjectMapper,
    private val lease: MonitorLease? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val locks = ConcurrentHashMap<String, Any>()

    @Scheduled(
        initialDelayString = "\${consistency.initial-delay-ms:10000}",
        fixedDelayString = "\${consistency.interval-ms:60000}"
    )
    fun runCycle() {
        if (lease != null && !lease.tryAcquire()) {
            log.debug("invariant cycle skipped: lease held by another instance")
            return
        }
        val cycleStart = System.nanoTime()
        val now = violationRepository.now()
        val summary = catalog.definitions.map { definition -> "${definition.id}=${check(definition, now)}" }
        val cycleDuration = Duration.ofNanos(System.nanoTime() - cycleStart)
        metrics.recordCycle(cycleDuration)
        log.info("invariant cycle done: duration_ms={} {}", cycleDuration.toMillis(), summary.joinToString(" "))
    }

    @Scheduled(
        initialDelayString = "\${consistency.full-sweep.initial-delay-ms:300000}",
        fixedDelayString = "\${consistency.full-sweep.interval-ms:21600000}"
    )
    fun runFullSweep() {
        if (lease != null && !lease.tryAcquire()) {
            log.debug("invariant full sweep skipped: lease held by another instance")
            return
        }
        val sweepStart = System.nanoTime()
        val summary = catalog.definitions.filter(checks::isSweepable).map { "${it.id}=${sweep(it)}" }
        val sweepDuration = Duration.ofNanos(System.nanoTime() - sweepStart)
        metrics.recordSweep(sweepDuration)
        log.info("invariant full sweep done: duration_ms={} {}", sweepDuration.toMillis(), summary.joinToString(" "))
    }

    private fun check(definition: InvariantDefinition, now: LocalDateTime): String {
        val start = System.nanoTime()
        val result = try {
            checks.recent(definition) { violationRepository.findOpen(definition.id).map { it.entityKey } }
        } catch (e: Exception) {
            metrics.recordError(definition.id)
            log.error("invariant check failed: inv={} error={}", definition.id, e.message)
            return "ERROR"
        } finally {
            metrics.recordDuration(definition.id, Duration.ofNanos(System.nanoTime() - start))
        }
        return synchronized(lockOf(definition.id)) { reconcile(definition, result, now) }
    }

    private fun reconcile(definition: InvariantDefinition, result: CheckResult, now: LocalDateTime): String {
        val observed = result.rows.associateBy(::entityKey)
        val open = violationRepository.findOpen(definition.id).associateBy { it.entityKey }
        val tally = upsert(definition, properties.policyOf(definition.id), observed, open, now)

        open.filterKeys { it !in observed && result.covers(it) }.values.forEach {
            violationRepository.resolve(it.id, now, SYSTEM_ACTOR, AUTO_RESOLUTION_NOTE)
        }

        metrics.recordResult(definition.id, tally.confirmed, observed.size - tally.confirmed, tally.ageMaxSeconds)
        return "${tally.confirmed}/${observed.size}"
    }

    private fun sweep(definition: InvariantDefinition): String {
        var observedCount = 0
        return try {
            checks.sweep(definition) { rows ->
                val observed = rows.associateBy(::entityKey)
                observedCount += observed.size
                synchronized(lockOf(definition.id)) {
                    val open = violationRepository.findOpen(definition.id).associateBy { it.entityKey }
                    upsert(definition, properties.policyOf(definition.id), observed, open, violationRepository.now())
                }
            }
            observedCount.toString()
        } catch (e: Exception) {
            metrics.recordError(definition.id)
            log.error("invariant full sweep failed: inv={} error={}", definition.id, e.message)
            "ERROR"
        }
    }

    private fun upsert(
        definition: InvariantDefinition,
        policy: InvariantPolicy,
        observed: Map<String, Map<String, Any?>>,
        open: Map<String, Violation>,
        now: LocalDateTime
    ): Tally {
        var confirmed = 0
        var ageMaxSeconds = 0.0
        observed.forEach { (entityKey, row) ->
            val snapshot = objectMapper.writeValueAsString(row)
            val existing = open[entityKey]
            val firstSeen = existing?.firstSeen ?: now
            val age = Duration.between(firstSeen, now)
            val confirmedAt = if (age >= policy.graceSeconds) now else null
            if (existing == null) {
                violationRepository.insert(definition.id, entityKey, policy.severity.name, now, confirmedAt, snapshot)
            } else {
                violationRepository.touch(existing.id, now, confirmedAt, snapshot)
            }
            if (confirmedAt != null || existing?.confirmedAt != null) {
                confirmed++
                ageMaxSeconds = maxOf(ageMaxSeconds, age.toMillis() / 1000.0)
            }
        }
        return Tally(confirmed, ageMaxSeconds)
    }

    private fun entityKey(row: Map<String, Any?>): String = row.values.first().toString()

    private fun lockOf(invariantId: String): Any = locks.computeIfAbsent(invariantId) { Any() }

    private class Tally(val confirmed: Int, val ageMaxSeconds: Double)

    companion object {
        const val SYSTEM_ACTOR = "system"
        const val AUTO_RESOLUTION_NOTE = "auto: no longer observed"
    }
}
