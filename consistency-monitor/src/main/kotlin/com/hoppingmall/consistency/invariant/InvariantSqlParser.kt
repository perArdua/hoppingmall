package com.hoppingmall.consistency.invariant

object InvariantSqlParser {

    private const val HEADER_PREFIX = "-- @INV "
    private val placeholder = Regex("""\{\{([A-Z_]+)}}""")

    fun parse(source: String, params: Map<String, String>): List<InvariantDefinition> {
        val definitions = mutableListOf<InvariantDefinition>()
        var id: String? = null
        var meaning = ""
        val statement = StringBuilder()

        source.lineSequence().map { it.trimEnd('\r') }.forEach { line ->
            when {
                line.startsWith(HEADER_PREFIX) -> {
                    val (headerId, headerMeaning) = line.removePrefix(HEADER_PREFIX).split(" | ", limit = 2)
                    id = headerId.trim()
                    meaning = headerMeaning.trim()
                    statement.clear()
                }
                line.isBlank() || line.trimStart().startsWith("--") -> Unit
                else -> {
                    if (statement.isNotEmpty()) statement.append(' ')
                    statement.append(line.trim())
                    if (line.trimEnd().endsWith(";")) {
                        val currentId = requireNotNull(id) { "SQL 블록 앞에 '-- @INV' 헤더가 없습니다: $line" }
                        definitions += InvariantDefinition(
                            id = currentId,
                            meaning = render(meaning, params),
                            sql = render(statement.toString().trimEnd(';', ' '), params)
                        )
                        statement.clear()
                    }
                }
            }
        }
        return definitions
    }

    private fun render(text: String, params: Map<String, String>): String =
        placeholder.replace(text) { match ->
            val key = match.groupValues[1]
            requireNotNull(params[key]) { "파라미터가 정의되지 않았습니다: $key" }
        }
}
