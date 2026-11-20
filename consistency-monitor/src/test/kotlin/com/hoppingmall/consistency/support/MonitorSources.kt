package com.hoppingmall.consistency.support

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.config.FullSweepProperties
import com.hoppingmall.consistency.config.SourceProperties
import com.hoppingmall.consistency.invariant.InvariantDefinition
import com.hoppingmall.consistency.invariant.InvariantSqlParser

object MonitorSources {

    val PARAMS = mapOf(
        "PENDING_MAX_MINUTES" to "10",
        "PAYING_MAX_MINUTES" to "15",
        "ORPHAN_GRACE_MINUTES" to "1",
        "OUTBOX_MAX_SECONDS" to "60",
        "DLQ_MAX_SECONDS" to "60",
        "POINT_PAYMENT_SINCE" to "2000-01-01 00:00:00"
    )

    val SOURCES = listOf("order", "payment", "product", "user", "notification")

    fun properties(
        urls: Map<String, String>,
        username: String,
        password: String,
        chunkSize: Long = 5000,
        lookupBatchSize: Int = 500
    ) = ConsistencyProperties(
        params = PARAMS,
        sources = urls.mapValues { (_, url) -> SourceProperties(url, username, password) },
        dataTimeZone = "+00:00",
        lookupBatchSize = lookupBatchSize,
        fullSweep = FullSweepProperties(chunkSize)
    )

    fun catalogSql(): String = resource("invariant-queries.sql")

    fun legacySql(): String = resource("legacy-invariants.sql")

    fun definitions(): List<InvariantDefinition> = InvariantSqlParser.parse(catalogSql(), PARAMS)

    private fun resource(name: String): String =
        MonitorSources::class.java.classLoader.getResource(name)!!.readText(Charsets.UTF_8)
}
