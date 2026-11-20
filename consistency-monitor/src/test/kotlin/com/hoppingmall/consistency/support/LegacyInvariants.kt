package com.hoppingmall.consistency.support

object LegacyInvariants {

    private const val HEADER_PREFIX = "-- @INV "
    private val placeholder = Regex("""\{\{([A-Z_]+)}}""")

    fun parse(source: String, params: Map<String, String>): Map<String, String> {
        val statements = linkedMapOf<String, String>()
        var id: String? = null
        val sql = StringBuilder()
        source.lineSequence().map { it.trimEnd('\r') }.forEach { line ->
            when {
                line.startsWith(HEADER_PREFIX) -> {
                    id = line.removePrefix(HEADER_PREFIX).substringBefore(" | ").trim()
                    sql.clear()
                }
                line.isBlank() || line.trimStart().startsWith("--") -> Unit
                else -> {
                    if (sql.isNotEmpty()) sql.append(' ')
                    sql.append(line.trim())
                    if (line.trimEnd().endsWith(";")) {
                        statements[id!!] = placeholder.replace(sql.toString().trimEnd(';', ' ')) { params.getValue(it.groupValues[1]) }
                        sql.clear()
                    }
                }
            }
        }
        return statements
    }
}
