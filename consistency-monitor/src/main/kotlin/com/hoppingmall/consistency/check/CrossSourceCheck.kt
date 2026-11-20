package com.hoppingmall.consistency.check

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.source.SourceDatabases

abstract class CrossSourceCheck<K : Any>(
    protected val sources: SourceDatabases,
    protected val properties: ConsistencyProperties
) {
    abstract val invariantId: String

    protected abstract fun toKey(value: Any): K

    protected abstract fun keyOfEntity(entityKey: String): K?

    protected abstract fun evaluate(definition: InvariantDefinition, keys: Set<K>): List<Map<String, Any?>>

    fun recent(definition: InvariantDefinition, openEntityKeys: Collection<String>): CheckResult {
        val window = mapOf(WINDOW_MINUTES to properties.recentWindowMinutes)
        val keys = definition.statements
            .filter { it.name.startsWith(RECENT_PREFIX) }
            .flatMapTo(mutableSetOf<K>()) { statement -> firstColumnKeys(sources.query(statement, window)) }
        openEntityKeys.mapNotNullTo(keys, ::keyOfEntity)
        val rows = if (keys.isEmpty()) emptyList() else evaluate(definition, keys)
        return CheckResult(rows) { entityKey -> keyOfEntity(entityKey)?.let { it in keys } ?: false }
    }

    fun sweep(definition: InvariantDefinition, onRows: (List<Map<String, Any?>>) -> Unit) {
        val bounds = sources.query(definition.statement(SWEEP_BOUNDS)).single()
        val minId = (bounds["min_id"] as Number?)?.toLong() ?: return
        val maxId = (bounds["max_id"] as Number).toLong()
        val sweep = definition.statement(SWEEP)
        var fromId = minId
        while (fromId <= maxId) {
            val toId = fromId + properties.fullSweep.chunkSize - 1
            val keys = firstColumnKeys(sources.query(sweep, mapOf(FROM_ID to fromId, TO_ID to toId)))
            if (keys.isNotEmpty()) onRows(evaluate(definition, keys))
            fromId = toId + 1
        }
    }

    protected fun lookup(definition: InvariantDefinition, name: String, ids: Collection<Any>): List<Map<String, Any?>> =
        if (ids.isEmpty()) emptyList()
        else sources.queryByIds(definition.statement(name), ids, properties.lookupBatchSize)

    protected fun keyOf(row: Map<String, Any?>, column: String): K? = row[column]?.let(::toKey)

    private fun firstColumnKeys(rows: List<Map<String, Any?>>): Set<K> =
        rows.mapNotNullTo(mutableSetOf()) { row -> row.values.first()?.let(::toKey) }

    companion object {
        const val RECENT_PREFIX = "recent-"
        const val SWEEP_BOUNDS = "sweep-bounds"
        const val SWEEP = "sweep"
        const val WINDOW_MINUTES = "windowMinutes"
        const val FROM_ID = "fromId"
        const val TO_ID = "toId"
    }
}

abstract class LongKeyCheck(
    sources: SourceDatabases,
    properties: ConsistencyProperties
) : CrossSourceCheck<Long>(sources, properties) {

    override fun toKey(value: Any): Long = (value as Number).toLong()
}

abstract class StringKeyCheck(
    sources: SourceDatabases,
    properties: ConsistencyProperties
) : CrossSourceCheck<String>(sources, properties) {

    override fun toKey(value: Any): String = value.toString()

    override fun keyOfEntity(entityKey: String): String = entityKey
}
