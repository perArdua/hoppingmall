package com.hoppingmall.cache

class FakeHotKeyDetector : HotKeyDetector {
    var overrideIsHot: Boolean = false
    var recordCount: Int = 0
        private set

    override fun recordAccess(key: String) {
        recordCount++
    }

    override fun isHot(key: String): Boolean = overrideIsHot

    fun reset() {
        overrideIsHot = false
        recordCount = 0
    }

    override fun close() {}
}
