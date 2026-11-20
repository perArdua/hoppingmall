package com.hoppingmall.consistency.invariant

data class InvariantDefinition(
    val id: String,
    val meaning: String,
    val statements: List<InvariantStatement>
) {
    fun statement(name: String): InvariantStatement =
        statements.firstOrNull { it.name == name } ?: throw IllegalArgumentException("$id 에 '$name' 문장이 없습니다")
}

data class InvariantStatement(
    val name: String,
    val source: String,
    val sql: String
)
