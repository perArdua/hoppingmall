package com.hoppingmall.consistency.check

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.source.SourceDatabases

class OrderPaymentPairCheck(
    override val invariantId: String,
    private val readOrdersFirst: Boolean,
    sources: SourceDatabases,
    properties: ConsistencyProperties
) : LongKeyCheck(sources, properties) {

    override fun keyOfEntity(entityKey: String): Long? = entityKey.toLongOrNull()

    override fun evaluate(definition: InvariantDefinition, keys: Set<Long>): List<Map<String, Any?>> {
        val orders: List<Map<String, Any?>>
        val payments: List<Map<String, Any?>>
        if (readOrdersFirst) {
            orders = lookup(definition, "orders-by-id", keys)
            payments = lookup(definition, "payments-by-order", keys)
        } else {
            payments = lookup(definition, "payments-by-order", keys)
            orders = lookup(definition, "orders-by-id", keys)
        }
        val paymentsByOrder = payments.groupBy { keyOf(it, "order_id") }
        return orders.flatMap { order ->
            paymentsByOrder[keyOf(order, "order_id")].orEmpty().map { payment ->
                linkedMapOf(
                    "order_id" to order["order_id"],
                    "order_status" to order["order_status"],
                    "payment_id" to payment["payment_id"],
                    "payment_status" to payment["payment_status"],
                    "order_updated_at" to order["order_updated_at"],
                    "payment_updated_at" to payment["payment_updated_at"]
                )
            }
        }
    }
}
