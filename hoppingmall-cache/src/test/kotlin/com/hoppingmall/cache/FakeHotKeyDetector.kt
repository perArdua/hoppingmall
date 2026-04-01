package com.hoppingmall.cache

import java.time.Duration

class FakeHotKeyDetector : HotKeyDetector(
    threshold = Long.MAX_VALUE,
    windowDuration = Duration.ofHours(1)
) {
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
}
