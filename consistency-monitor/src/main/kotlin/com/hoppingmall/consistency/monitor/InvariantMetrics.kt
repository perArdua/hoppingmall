package com.hoppingmall.consistency.monitor

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantCatalog
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class InvariantMetrics(
    private val meterRegistry: MeterRegistry,
    catalog: InvariantCatalog,
    properties: ConsistencyProperties
) {
    private class State {
        @Volatile var count: Double = 0.0
        @Volatile var candidates: Double = 0.0
        @Volatile var ageMaxSeconds: Double = 0.0
    }

    private val states = ConcurrentHashMap<String, State>()
    private val timers = ConcurrentHashMap<String, Timer>()
    private val errors = ConcurrentHashMap<String, Counter>()
    private val cycleTimer: Timer = Timer.builder("invariant.check.cycle.duration").register(meterRegistry)
    @Volatile private var lastCycleSeconds: Double = 0.0

    init {
        Gauge.builder("invariant.check.cycle.last.duration.seconds") { lastCycleSeconds }.register(meterRegistry)
        catalog.definitions.forEach { definition ->
            val id = definition.id
            val severity = properties.policyOf(id).severity.name
            val state = State()
            states[id] = state
            Gauge.builder("invariant.violation.count") { state.count }
                .tags("inv", id, "severity", severity).register(meterRegistry)
            Gauge.builder("invariant.violation.candidates") { state.candidates }
                .tags("inv", id, "severity", severity).register(meterRegistry)
            Gauge.builder("invariant.violation.age.seconds.max") { state.ageMaxSeconds }
                .tags("inv", id, "severity", severity).register(meterRegistry)
            timers[id] = Timer.builder("invariant.check.duration").tags("inv", id).register(meterRegistry)
            errors[id] = Counter.builder("invariant.check.errors").tags("inv", id).register(meterRegistry)
        }
    }

    fun recordResult(invId: String, confirmed: Int, candidates: Int, ageMaxSeconds: Double) {
        val state = states.getValue(invId)
        state.count = confirmed.toDouble()
        state.candidates = candidates.toDouble()
        state.ageMaxSeconds = ageMaxSeconds
    }

    fun recordDuration(invId: String, duration: Duration) {
        timers.getValue(invId).record(duration)
    }

    fun recordError(invId: String) {
        errors.getValue(invId).increment()
    }

    fun recordCycle(duration: Duration) {
        cycleTimer.record(duration)
        lastCycleSeconds = duration.toNanos() / 1_000_000_000.0
    }
}
