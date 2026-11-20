package com.hoppingmall.consistency.check

class CheckResult(
    val rows: List<Map<String, Any?>>,
    private val coverage: (String) -> Boolean
) {
    fun covers(entityKey: String): Boolean = coverage(entityKey)

    companion object {
        fun complete(rows: List<Map<String, Any?>>): CheckResult = CheckResult(rows) { true }
    }
}
