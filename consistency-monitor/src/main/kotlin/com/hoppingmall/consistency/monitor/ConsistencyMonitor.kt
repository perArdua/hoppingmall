package com.hoppingmall.consistency.monitor

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.violation.ViolationRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

@Component
class ConsistencyMonitor(
    private val catalog: InvariantCatalog,
    private val properties: ConsistencyProperties,
    private val jdbcTemplate: JdbcTemplate,
    private val violationRepository: ViolationRepository,
    private val metrics: InvariantMetrics,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${consistency.initial-delay-ms:10000}",
        fixedDelayString = "\${consistency.interval-ms:60000}"
    )
    fun runCycle() {
        val cycleStart = System.nanoTime()
        val now = violationRepository.now()
        val summary = catalog.definitions.map { definition -> "${definition.id}=${check(definition, now)}" }
        val cycleDuration = Duration.ofNanos(System.nanoTime() - cycleStart)
        metrics.recordCycle(cycleDuration)
        log.info("invariant cycle done: duration_ms={} {}", cycleDuration.toMillis(), summary.joinToString(" "))
    }

    private fun check(definition: InvariantDefinition, now: LocalDateTime): String {
        val policy = properties.policyOf(definition.id)
        val start = System.nanoTime()
        val rows = try {
            jdbcTemplate.queryForList(definition.sql)
        } catch (e: Exception) {
            metrics.recordError(definition.id)
            log.error("invariant check failed: inv={} error={}", definition.id, e.message)
            return "ERROR"
        } finally {
            metrics.recordDuration(definition.id, Duration.ofNanos(System.nanoTime() - start))
        }

        val observed = rows.associateBy { it.values.first().toString() }
        val open = violationRepository.findOpen(definition.id).associateBy { it.entityKey }
        var confirmed = 0
        var ageMaxSeconds = 0.0

        observed.forEach { (entityKey, row) ->
            val snapshot = objectMapper.writeValueAsString(row)
            val existing = open[entityKey]
            val firstSeen = existing?.firstSeen ?: now
            val age = Duration.between(firstSeen, now)
            val confirmedAt = if (age.seconds >= policy.graceSeconds) now else null
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

        open.filterKeys { it !in observed }.values.forEach {
            violationRepository.resolve(it.id, now, SYSTEM_ACTOR, AUTO_RESOLUTION_NOTE)
        }

        metrics.recordResult(definition.id, confirmed, observed.size - confirmed, ageMaxSeconds)
        return "$confirmed/${observed.size}"
    }

    companion object {
        const val SYSTEM_ACTOR = "system"
        const val AUTO_RESOLUTION_NOTE = "auto: no longer observed"
    }
}
