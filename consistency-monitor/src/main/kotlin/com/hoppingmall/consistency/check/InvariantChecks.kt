package com.hoppingmall.consistency.check

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.source.SourceDatabases
import org.springframework.stereotype.Component

@Component
class InvariantChecks(
    catalog: InvariantCatalog,
    private val sources: SourceDatabases,
    properties: ConsistencyProperties
) {
    private val crossSourceChecks: Map<String, CrossSourceCheck<*>> = listOf(
        OrderPaymentPairCheck("INV-1", readOrdersFirst = true, sources, properties),
        OrderPaymentPairCheck("INV-1R", readOrdersFirst = false, sources, properties),
        CancelledOrderReservationCheck(sources, properties),
        OrphanReservationCheck(sources, properties),
        PaymentEventOrderCheck(sources, properties)
    ).associateBy { it.invariantId }

    init {
        val missing = catalog.definitions.flatMap { it.statements }.map { it.source }.toSet() - sources.names
        require(missing.isEmpty()) { "점검 SQL이 쓰는 원본 DB의 연결 설정이 없습니다: ${missing.sorted()}" }
    }

    fun recent(definition: InvariantDefinition, openEntityKeys: () -> Collection<String>): CheckResult {
        val crossSourceCheck = crossSourceChecks[definition.id]
            ?: return CheckResult.complete(definition.statements.flatMap { sources.query(it) })
        return crossSourceCheck.recent(definition, openEntityKeys())
    }

    fun isSweepable(definition: InvariantDefinition): Boolean = definition.id in crossSourceChecks

    fun sweep(definition: InvariantDefinition, onRows: (List<Map<String, Any?>>) -> Unit) {
        crossSourceChecks.getValue(definition.id).sweep(definition, onRows)
    }
}
