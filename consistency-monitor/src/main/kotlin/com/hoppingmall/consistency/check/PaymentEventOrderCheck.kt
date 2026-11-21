package com.hoppingmall.consistency.check

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.source.SourceDatabases
import java.time.LocalDateTime

class PaymentEventOrderCheck(
    sources: SourceDatabases,
    properties: ConsistencyProperties
) : LongKeyCheck(sources, properties) {

    override val invariantId: String = "INV-10"

    override fun keyOfEntity(entityKey: String): Long? = entityKey.substringBefore(":").toLongOrNull()

    override fun evaluate(definition: InvariantDefinition, keys: Set<Long>): List<Map<String, Any?>> {
        val aggregateIds = lookup(definition, "payments-by-order", keys).map { it["payment_id"].toString() } +
            keys.map(Long::toString)
        val events = lookup(definition, "events-by-aggregate", aggregateIds).filter { keyOf(it, "order_id") in keys }
        val processedAt = lookup(definition, "saga-logs-by-event", events.mapNotNull { it["saga_event_id"] })
            .associate { it["event_id"].toString() to it["created_at"] as LocalDateTime }
        return events
            .mapNotNull { event ->
                event["saga_event_id"]?.toString()?.let(processedAt::get)?.let { ProcessedEvent(event, it) }
            }
            .groupBy { keyOf(it.event, "order_id") }
            .values
            .flatMap(::outOfOrderPairs)
    }

    private fun outOfOrderPairs(events: List<ProcessedEvent>): List<Map<String, Any?>> =
        events.flatMap { earlier ->
            events.filter { later ->
                later.outboxId > earlier.outboxId && earlier.processedAt > later.processedAt && earlier.sharesOrderingWith(later)
            }
                .map { later ->
                    linkedMapOf(
                        "entity" to "${earlier.event["order_id"]}:${earlier.outboxId}>${later.outboxId}",
                        "order_id" to earlier.event["order_id"],
                        "earlier_event" to earlier.event["event_type"],
                        "later_event" to later.event["event_type"],
                        "earlier_processed_at" to earlier.processedAt,
                        "later_processed_at" to later.processedAt
                    )
                }
        }

    private class ProcessedEvent(val event: Map<String, Any?>, val processedAt: LocalDateTime) {
        val outboxId: Long = (event["outbox_id"] as Number).toLong()
        private val topic: String = event["topic"].toString()
        private val cancellationResult: Boolean = event["event_type"].toString().startsWith(CANCELLATION_RESULT_PREFIX)

        fun sharesOrderingWith(other: ProcessedEvent): Boolean =
            topic != other.topic || cancellationResult == other.cancellationResult
    }

    private companion object {
        const val CANCELLATION_RESULT_PREFIX = "PaymentCancellation"
    }
}
