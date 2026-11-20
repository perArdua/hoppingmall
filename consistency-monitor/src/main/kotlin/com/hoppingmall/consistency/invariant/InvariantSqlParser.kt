package com.hoppingmall.consistency.invariant

object InvariantSqlParser {

    private const val INVARIANT_PREFIX = "-- @INV "
    private const val STATEMENT_PREFIX = "-- @SQL "
    private val placeholder = Regex("""\{\{([A-Z_]+)}}""")
    private val whitespace = Regex("""\s+""")

    fun parse(source: String, params: Map<String, String>): List<InvariantDefinition> {
        val definitions = mutableListOf<InvariantDefinition>()
        var current: InvariantDefinition? = null
        var header: Pair<String, String>? = null
        val sql = StringBuilder()

        source.lineSequence().map { it.trimEnd('\r') }.forEach { line ->
            when {
                line.startsWith(INVARIANT_PREFIX) -> {
                    current?.let { definitions += finish(it) }
                    val (id, meaning) = line.removePrefix(INVARIANT_PREFIX).split(" | ", limit = 2)
                    current = InvariantDefinition(id.trim(), render(meaning.trim(), params), emptyList())
                    header = null
                    sql.clear()
                }
                line.startsWith(STATEMENT_PREFIX) -> {
                    requireNotNull(current) { "'-- @SQL' 앞에 '-- @INV' 헤더가 없습니다: $line" }
                    val parts = line.removePrefix(STATEMENT_PREFIX).trim().split(whitespace)
                    require(parts.size == 2) { "'-- @SQL <원본 DB> <이름>' 형식이 아닙니다: $line" }
                    header = parts[0] to parts[1]
                    sql.clear()
                }
                line.isBlank() || line.trimStart().startsWith("--") -> Unit
                else -> {
                    if (sql.isNotEmpty()) sql.append(' ')
                    sql.append(line.trim())
                    if (line.trimEnd().endsWith(";")) {
                        val definition = requireNotNull(current) { "SQL 문장 앞에 '-- @INV' 헤더가 없습니다: $line" }
                        val (statementSource, name) = requireNotNull(header) { "SQL 문장 앞에 '-- @SQL' 헤더가 없습니다: $line" }
                        require(definition.statements.none { it.name == name }) { "${definition.id} 에 '$name' 문장이 두 번 있습니다" }
                        val statement = InvariantStatement(name, statementSource, render(sql.toString().trimEnd(';', ' '), params))
                        current = definition.copy(statements = definition.statements + statement)
                        header = null
                        sql.clear()
                    }
                }
            }
        }
        current?.let { definitions += finish(it) }
        return definitions
    }

    private fun finish(definition: InvariantDefinition): InvariantDefinition {
        require(definition.statements.isNotEmpty()) { "${definition.id} 에 SQL 문장이 없습니다" }
        return definition
    }

    private fun render(text: String, params: Map<String, String>): String =
        placeholder.replace(text) { match ->
            val key = match.groupValues[1]
            requireNotNull(params[key]) { "파라미터가 정의되지 않았습니다: $key" }
        }
}
