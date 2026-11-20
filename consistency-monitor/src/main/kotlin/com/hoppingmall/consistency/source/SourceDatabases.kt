package com.hoppingmall.consistency.source

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.config.SourceProperties
import com.hoppingmall.consistency.invariant.InvariantStatement
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.annotation.PreDestroy
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class SourceDatabases(properties: ConsistencyProperties) {

    private val dataSources: Map<String, HikariDataSource> = properties.sources.mapValues { (name, source) ->
        HikariDataSource(config(name, source, properties.dataTimeZone))
    }
    private val templates: Map<String, NamedParameterJdbcTemplate> =
        dataSources.mapValues { NamedParameterJdbcTemplate(it.value) }

    val names: Set<String> get() = templates.keys

    fun query(statement: InvariantStatement, params: Map<String, Any?> = emptyMap()): List<Map<String, Any?>> {
        val template = templates[statement.source]
            ?: throw IllegalStateException("설정에 없는 원본 DB입니다: ${statement.source}")
        return template.queryForList(statement.sql, params)
    }

    fun queryByIds(statement: InvariantStatement, ids: Collection<Any>, batchSize: Int): List<Map<String, Any?>> =
        ids.distinct().chunked(batchSize).flatMap { query(statement, mapOf(IDS to it)) }

    @PreDestroy
    fun close() {
        dataSources.values.forEach { it.close() }
    }

    private fun config(name: String, source: SourceProperties, dataTimeZone: String) = HikariConfig().apply {
        poolName = "source-$name"
        jdbcUrl = source.url
        username = source.username
        password = source.password
        maximumPoolSize = source.maximumPoolSize
        minimumIdle = 0
        isReadOnly = true
        initializationFailTimeout = -1
        connectionInitSql = "SET time_zone = '$dataTimeZone'"
    }

    companion object {
        const val IDS = "ids"
    }
}
