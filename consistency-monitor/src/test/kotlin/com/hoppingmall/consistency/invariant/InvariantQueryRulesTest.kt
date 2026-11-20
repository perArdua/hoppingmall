package com.hoppingmall.consistency.invariant

import com.hoppingmall.consistency.check.CrossSourceCheck
import com.hoppingmall.consistency.support.FlywaySchemas
import com.hoppingmall.consistency.support.MonitorSources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("점검 SQL 규칙: 한 문장은 자기 원본 DB의 표만 읽는다")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InvariantQueryRulesTest {

    private val tablesBySource = FlywaySchemas.serviceOfSource.mapValues { (_, service) -> FlywaySchemas.tablesOf(service) }
    private val indexesBySource = FlywaySchemas.serviceOfSource.mapValues { (_, service) -> FlywaySchemas.indexesOf(service) }

    private fun violationsOf(statement: InvariantStatement): List<String> {
        val sql = statement.sql.replace(STRING_LITERAL, "''")
        val tables = tablesBySource[statement.source] ?: return listOf("알 수 없는 원본 DB '${statement.source}'")
        val prefixed = DATABASE_PREFIX.findAll(sql).map { "DB 접두사 '${it.value}'" }
        val foreign = TABLE_REFERENCE.findAll(sql).map { it.groupValues[1].lowercase() }
            .filter { it !in tables }
            .map { "'${statement.source}' DB에 없는 표 '$it'" }
        val indexes = indexesBySource.getValue(statement.source)
        val unknownIndexes = INDEX_HINT.findAll(sql).flatMap { hint ->
            val table = hint.groupValues[1].lowercase()
            hint.groupValues[2].split(',').map { it.trim().trim('`').lowercase() }.filter { it.isNotEmpty() }
                .filter { it !in indexes[table].orEmpty() }
                .map { "'${statement.source}' DB의 '$table' 표에 없는 인덱스 힌트 '$it'" }
        }
        return (prefixed + foreign + unknownIndexes).toList()
    }

    @Test
    fun 모든_점검_문장은_DB_접두사를_쓰지_않고_자기_원본_DB의_Flyway_표만_참조한다() {
        val violations = MonitorSources.definitions().flatMap { definition ->
            definition.statements.flatMap { statement ->
                violationsOf(statement).map { "${definition.id} ${statement.name}: $it" }
            }
        }

        assertThat(violations).isEmpty()
    }

    @Test
    fun 규칙은_DB_접두사와_다른_DB의_표를_잡고_문자열_안의_DB_이름은_허용한다() {
        assertThat(violationsOf(InvariantStatement("check", "payment", "SELECT * FROM order_db.orders o")))
            .anyMatch { it.contains("order_db.") }
        assertThat(violationsOf(InvariantStatement("check", "payment", "SELECT * FROM payments p JOIN orders o ON o.id = p.order_id")))
            .containsExactly("'payment' DB에 없는 표 'orders'")
        assertThat(violationsOf(InvariantStatement("check", "warehouse", "SELECT 1")))
            .containsExactly("알 수 없는 원본 DB 'warehouse'")
        assertThat(violationsOf(InvariantStatement("check", "payment", "SELECT CONCAT('payment_db.outbox_events:', e.id) FROM outbox_events e")))
            .isEmpty()
    }

    @Test
    fun 인덱스_힌트는_원본_서비스_Flyway에_있는_그_표의_인덱스만_쓴다() {
        assertThat(violationsOf(InvariantStatement("check", "payment", "SELECT 1 FROM outbox_events e FORCE INDEX (idx_outbox_processed_processed_at) WHERE e.processed = 1")))
            .isEmpty()
        assertThat(violationsOf(InvariantStatement("check", "payment", "SELECT 1 FROM outbox_events AS e USE INDEX FOR JOIN (`PRIMARY`, idx_outbox_aggregate_id)")))
            .isEmpty()
        assertThat(violationsOf(InvariantStatement("check", "payment", "SELECT 1 FROM outbox_events e FORCE INDEX (idx_outbox_no_such_index)")))
            .containsExactly("'payment' DB의 'outbox_events' 표에 없는 인덱스 힌트 'idx_outbox_no_such_index'")
        assertThat(violationsOf(InvariantStatement("check", "payment", "SELECT 1 FROM payments p IGNORE KEY (idx_outbox_processed_processed_at)")))
            .containsExactly("'payment' DB의 'payments' 표에 없는 인덱스 힌트 'idx_outbox_processed_processed_at'")
        assertThat(violationsOf(InvariantStatement("check", "order", "SELECT 1 FROM orders o FORCE INDEX (idx_payments_order_id)")))
            .containsExactly("'order' DB의 'orders' 표에 없는 인덱스 힌트 'idx_payments_order_id'")
    }

    @Test
    fun 점검_문장의_인덱스_힌트를_규칙이_읽는다() {
        val hinted = MonitorSources.definitions().first { it.id == "INV-10" }.statement("recent-events")

        val hint = INDEX_HINT.find(hinted.sql)

        assertThat(hint?.groupValues?.drop(1)).containsExactly("outbox_events", "idx_outbox_processed_processed_at")
        assertThat(indexesBySource.getValue("payment").getValue("outbox_events")).contains("idx_outbox_processed_processed_at")
        assertThat(violationsOf(hinted)).isEmpty()
    }

    @Test
    fun DB를_걸치는_점검은_최근_후보와_전체_훑기_문장을_모두_가진다() {
        val crossSource = MonitorSources.definitions().filter { it.statements.map(InvariantStatement::source).distinct().size > 1 }
            .filter { definition -> definition.statements.any { it.name.startsWith(CrossSourceCheck.RECENT_PREFIX) } }

        assertThat(crossSource.map { it.id }).containsExactly("INV-1", "INV-1R", "INV-3a", "INV-3b", "INV-10")
        assertThat(crossSource).allSatisfy { definition ->
            assertThat(definition.statements.map { it.name })
                .contains(CrossSourceCheck.SWEEP_BOUNDS, CrossSourceCheck.SWEEP)
            assertThat(definition.statement(CrossSourceCheck.SWEEP).source)
                .isEqualTo(definition.statement(CrossSourceCheck.SWEEP_BOUNDS).source)
            assertThat(definition.statement(CrossSourceCheck.SWEEP).sql).contains(":fromId", ":toId")
        }
    }

    companion object {
        private val STRING_LITERAL = Regex("""'(?:[^']|'')*'""")
        private val DATABASE_PREFIX = Regex("""\b[a-z][a-z0-9_]*_db\.""", RegexOption.IGNORE_CASE)
        private val TABLE_REFERENCE = Regex("""\b(?:FROM|JOIN)\s+`?([a-z_][a-z0-9_]*)`?""", RegexOption.IGNORE_CASE)
        private val INDEX_HINT = Regex(
            """\b(?:FROM|JOIN)\s+`?([a-z_][a-z0-9_]*)`?(?:\s+(?:AS\s+)?(?!(?:FORCE|USE|IGNORE)\b)[a-z_][a-z0-9_]*)?\s+(?:FORCE|USE|IGNORE)\s+(?:INDEX|KEY)(?:\s+FOR\s+(?:JOIN|ORDER\s+BY|GROUP\s+BY))?\s*\(([^)]*)\)""",
            RegexOption.IGNORE_CASE
        )
    }
}
