package com.hoppingmall.consistency.config

import com.hoppingmall.consistency.enums.RepairPolicy
import com.hoppingmall.consistency.enums.Severity
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "consistency")
data class ConsistencyProperties(
    val sqlResource: String = "classpath:invariants.sql",
    val params: Map<String, String> = emptyMap(),
    val policies: Map<String, InvariantPolicy> = emptyMap(),
    val defaultPolicy: InvariantPolicy = InvariantPolicy()
) {
    fun policyOf(invariantId: String): InvariantPolicy = policies[invariantId] ?: defaultPolicy
}

data class InvariantPolicy(
    val severity: Severity = Severity.WARN,
    val graceSeconds: Long = 0,
    val repairPolicy: RepairPolicy = RepairPolicy.HUMAN
)
