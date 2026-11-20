package com.hoppingmall.consistency.check

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.source.SourceDatabases

class CancelledOrderReservationCheck(
    sources: SourceDatabases,
    properties: ConsistencyProperties
) : StringKeyCheck(sources, properties) {

    override val invariantId: String = "INV-3a"

    override fun evaluate(definition: InvariantDefinition, keys: Set<String>): List<Map<String, Any?>> {
        val items = lookup(definition, "order-items-by-reservation", keys).groupBy { keyOf(it, "reservation_id") }
        val reservations = lookup(definition, "reservations-by-id", keys)
        return reservations.flatMap { reservation ->
            items[keyOf(reservation, "reservation_id")].orEmpty().map { item ->
                linkedMapOf(
                    "reservation_id" to reservation["reservation_id"],
                    "order_id" to item["order_id"],
                    "order_status" to item["order_status"],
                    "order_item_id" to item["order_item_id"],
                    "reservation_status" to reservation["reservation_status"],
                    "quantity" to reservation["quantity"]
                )
            }
        }
    }
}
