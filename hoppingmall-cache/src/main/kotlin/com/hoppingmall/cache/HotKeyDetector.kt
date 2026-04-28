package com.hoppingmall.cache

import java.io.Closeable

interface HotKeyDetector : Closeable {
    fun recordAccess(key: String)
    fun isHot(key: String): Boolean
}
