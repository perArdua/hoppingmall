package com.hoppingmall.consistency.invariant

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("InvariantSqlParser")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InvariantSqlParserTest {

    private val params = mapOf(
        "PENDING_MAX_MINUTES" to "10",
        "PAYING_MAX_MINUTES" to "15",
        "ORPHAN_GRACE_MINUTES" to "1",
        "OUTBOX_MAX_SECONDS" to "60",
        "DLQ_MAX_SECONDS" to "60"
    )

    private fun bundledSql(): String =
        javaClass.classLoader.getResource("invariants.sql")!!.readText(Charsets.UTF_8)

    @Test
    fun 빌드에_복사된_스크립트_SQL에서_모든_항목을_순서대로_읽는다() {
        val definitions = InvariantSqlParser.parse(bundledSql(), params)

        assertThat(definitions.map { it.id }).containsExactly(
            "INV-1", "INV-1R", "INV-2a", "INV-2b", "INV-3a", "INV-3b",
            "INV-4", "INV-5", "INV-6", "INV-7", "INV-9", "INV-10", "INV-11", "INV-12"
        )
        assertThat(definitions).allSatisfy {
            assertThat(it.sql).startsWith("SELECT").doesNotContain("{{").doesNotEndWith(";")
        }
        assertThat(definitions.first { it.id == "INV-2b" }.meaning).isEqualTo("주문이 15분 넘게 PAYING")
    }

    @Test
    fun 블록_사이의_주석과_빈_줄은_무시하고_여러_줄을_한_문장으로_합친다() {
        val sql = """
            -- @INV INV-X | 테스트 {{PAYING_MAX_MINUTES}}분
            SELECT a
            -- 중간 주석
            FROM t
            WHERE b < {{PAYING_MAX_MINUTES}};
        """.trimIndent()

        val definition = InvariantSqlParser.parse(sql, params).single()

        assertThat(definition.id).isEqualTo("INV-X")
        assertThat(definition.meaning).isEqualTo("테스트 15분")
        assertThat(definition.sql).isEqualTo("SELECT a FROM t WHERE b < 15")
    }

    @Test
    fun 정의되지_않은_파라미터가_있으면_실패한다() {
        val sql = "-- @INV INV-X | m\nSELECT {{UNKNOWN}};"

        assertThatThrownBy { InvariantSqlParser.parse(sql, params) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("UNKNOWN")
    }

    @Test
    fun 헤더_없는_SQL_블록이면_실패한다() {
        assertThatThrownBy { InvariantSqlParser.parse("SELECT 1;", params) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
