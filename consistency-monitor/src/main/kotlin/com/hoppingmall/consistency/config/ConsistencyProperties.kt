package com.hoppingmall.consistency.config

import com.hoppingmall.consistency.enums.Severity
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

@ConfigurationProperties(prefix = "consistency")
data class ConsistencyProperties(
    val sqlResource: String = "classpath:invariant-queries.sql",
    val params: Map<String, String> = emptyMap(),
    val policies: Map<String, InvariantPolicy> = emptyMap(),
    val defaultPolicy: InvariantPolicy = InvariantPolicy(),
    val sources: Map<String, SourceProperties> = emptyMap(),
    val dataTimeZone: String = "+09:00",
    val recentWindowMinutes: Long = 10,
    val lookupBatchSize: Int = 500,
    val fullSweep: FullSweepProperties = FullSweepProperties()
) {
    fun policyOf(invariantId: String): InvariantPolicy = policies[invariantId] ?: defaultPolicy
}

data class InvariantPolicy(
    val severity: Severity = Severity.WARN,
    @DurationUnit(ChronoUnit.SECONDS)
    val graceSeconds: Duration = Duration.ZERO
)

data class SourceProperties(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val maximumPoolSize: Int = 2
)

data class FullSweepProperties(
    val chunkSize: Long = 5000
)
