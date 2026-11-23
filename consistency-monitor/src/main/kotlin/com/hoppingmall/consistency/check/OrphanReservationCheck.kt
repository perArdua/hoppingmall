package com.hoppingmall.consistency.check

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.source.SourceDatabases

class OrphanReservationCheck(
    sources: SourceDatabases,
    properties: ConsistencyProperties
) : StringKeyCheck(sources, properties) {

    override val invariantId: String = "INV-3b"

    override fun evaluate(definition: InvariantDefinition, keys: Set<String>): List<Map<String, Any?>> {
        val reservations = lookup(definition, "reservations-by-id", keys)
        val referenced = lookup(definition, "order-items-by-reservation", reservations.mapNotNull { it["reservation_id"] })
            .mapNotNullTo(mutableSetOf()) { keyOf(it, "reservation_id") }
        return reservations.filter { keyOf(it, "reservation_id") !in referenced }
    }
}
