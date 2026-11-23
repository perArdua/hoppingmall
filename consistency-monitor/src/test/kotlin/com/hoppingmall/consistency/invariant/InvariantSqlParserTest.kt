package com.hoppingmall.consistency.invariant

import com.hoppingmall.consistency.support.MonitorSources
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("InvariantSqlParser")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InvariantSqlParserTest {

    private val params = MonitorSources.PARAMS + ("POINT_PAYMENT_SINCE" to "2026-11-16 06:02:19")

    private fun bundled(): List<InvariantDefinition> = InvariantSqlParser.parse(MonitorSources.catalogSql(), params)

    @Test
    fun 모니터_SQL에서_모든_항목을_순서대로_읽는다() {
        val definitions = bundled()

        assertThat(definitions.map { it.id }).containsExactly(
            "INV-1", "INV-1R", "INV-2a", "INV-2b", "INV-3a", "INV-3b",
            "INV-4", "INV-5", "INV-13", "INV-14", "INV-6", "INV-7", "INV-9", "INV-10", "INV-11", "INV-12"
        )
        assertThat(definitions.flatMap { it.statements }).allSatisfy {
            assertThat(it.sql).startsWith("SELECT").doesNotContain("{{").doesNotEndWith(";")
        }
        assertThat(definitions.first { it.id == "INV-2b" }.meaning).isEqualTo("주문이 15분 넘게 PAYING")
    }

    @Test
    fun 문장마다_원본_DB와_이름을_읽는다() {
        val definition = bundled().first { it.id == "INV-12" }

        assertThat(definition.statements.map { it.source })
            .containsExactly("order", "payment", "product", "user", "notification")
        assertThat(definition.statement("check-user").sql).contains("FROM dlq_messages d")
    }

    @Test
    fun INV_14는_기준_시각_전에_만든_결제와_이력의_되돌림을_보지_않는다() {
        val definition = bundled().first { it.id == "INV-14" }

        assertThat(definition.statement("check").sql)
            .contains("h.created_at >= '2026-11-16 06:02:19'")
            .contains("p.created_at < '2026-11-16 06:02:19'")
        assertThat(definition.meaning).contains("2026-11-16 06:02:19 이후 결제만")
    }

    @Test
    fun 블록_사이의_주석과_빈_줄은_무시하고_여러_줄을_한_문장으로_합친다() {
        val sql = """
            -- @INV INV-X | 테스트 {{PAYING_MAX_MINUTES}}분
            -- @SQL order check
            SELECT a
            -- 중간 주석
            FROM t
            WHERE b < {{PAYING_MAX_MINUTES}};

            -- @SQL payment second
            SELECT c FROM u;
        """.trimIndent()

        val definition = InvariantSqlParser.parse(sql, params).single()

        assertThat(definition.id).isEqualTo("INV-X")
        assertThat(definition.meaning).isEqualTo("테스트 15분")
        assertThat(definition.statements).containsExactly(
            InvariantStatement("check", "order", "SELECT a FROM t WHERE b < 15"),
            InvariantStatement("second", "payment", "SELECT c FROM u")
        )
    }

    @Test
    fun 없는_문장_이름을_찾으면_실패한다() {
        val definition = InvariantSqlParser.parse("-- @INV INV-X | m\n-- @SQL order check\nSELECT 1;", params).single()

        assertThatThrownBy { definition.statement("missing") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("missing")
    }

    @Test
    fun 정의되지_않은_파라미터가_있으면_실패한다() {
        val sql = "-- @INV INV-X | m\n-- @SQL order check\nSELECT {{UNKNOWN}};"

        assertThatThrownBy { InvariantSqlParser.parse(sql, params) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("UNKNOWN")
    }

    @Test
    fun 헤더_없는_SQL_블록이면_실패한다() {
        assertThatThrownBy { InvariantSqlParser.parse("SELECT 1;", params) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InvariantSqlParser.parse("-- @INV INV-X | m\nSELECT 1;", params) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InvariantSqlParser.parse("-- @SQL order check\nSELECT 1;", params) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 원본_DB와_이름이_없는_문장_헤더나_겹치는_이름이나_빈_항목이면_실패한다() {
        assertThatThrownBy { InvariantSqlParser.parse("-- @INV INV-X | m\n-- @SQL order\nSELECT 1;", params) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            InvariantSqlParser.parse("-- @INV INV-X | m\n-- @SQL order a\nSELECT 1;\n-- @SQL order a\nSELECT 2;", params)
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("두 번")
        assertThatThrownBy { InvariantSqlParser.parse("-- @INV INV-X | m\n-- @INV INV-Y | n\n-- @SQL order a\nSELECT 1;", params) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("INV-X")
    }
}
