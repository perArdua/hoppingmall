package com.hoppingmall.consistency.invariant

import com.hoppingmall.consistency.support.FlywaySchemas
import com.hoppingmall.consistency.support.MonitorSources
import com.hoppingmall.consistency.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

@DisplayName("점검 문장마다 원본 서비스 Flyway 스키마에서 EXPLAIN이 돈다")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InvariantStatementExplainMySqlTest {

    private fun statementsOf(source: String): List<String> =
        MonitorSources.definitions()
            .flatMap { definition -> definition.statements.filter { it.source == source } }
            .map(::bind)

    private fun bind(statement: InvariantStatement): String =
        statement.sql
            .replace(":windowMinutes", WINDOW_MINUTES)
            .replace(":fromId", "1")
            .replace(":toId", CHUNK_SIZE)
            .replace(":ids", sampleIds(statement.name))

    private fun sampleIds(statementName: String): String = (1..SAMPLE_IDS).joinToString(", ") { n ->
        when (statementName) {
            "events-by-aggregate" -> "'$n'"
            "order-items-by-reservation", "reservations-by-id" -> "'sample-reservation-$n'"
            "saga-logs-by-event" -> "'payment-completed-sample-$n'"
            else -> "$n"
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["order", "payment", "product", "user", "notification"])
    fun 원본_DB마다_모든_점검_문장이_서비스_Flyway_스키마에서_오류_없이_돈다(source: String) {
        val url = MySqlContainerSupport.jdbcUrl("explain_${source}_db")
        FlywaySchemas.migrate(url, MySqlContainerSupport.username, MySqlContainerSupport.password, FlywaySchemas.serviceOfSource.getValue(source))
        val jdbc = JdbcTemplate(DriverManagerDataSource(url, MySqlContainerSupport.username, MySqlContainerSupport.password))
        val statements = statementsOf(source)

        assertThat(statements).isNotEmpty()
        statements.forEach { sql ->
            assertThat(sql).doesNotContain(":ids", ":windowMinutes", ":fromId", ":toId")
            assertThat(jdbc.queryForList("EXPLAIN $sql")).`as`(sql.take(80)).isNotEmpty()
            assertThat(jdbc.queryForList("EXPLAIN ANALYZE $sql")).`as`(sql.take(80)).isNotEmpty()
        }
    }

    companion object {
        private const val WINDOW_MINUTES = "10"
        private const val CHUNK_SIZE = "5000"
        private const val SAMPLE_IDS = 500
    }
}
