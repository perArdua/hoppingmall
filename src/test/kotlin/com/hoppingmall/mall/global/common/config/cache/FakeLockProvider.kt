package com.hoppingmall.mall.global.common.config.cache

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class FakeLockProvider : LockProvider {

    private val locks = ConcurrentHashMap<String, Boolean>()
    var lockCallCount = 0
        private set
    var unlockCallCount = 0
        private set

    override fun tryLock(key: String, leaseTime: Duration): Boolean {
        lockCallCount++
        return locks.putIfAbsent(key, true) == null
    }

    override fun unlock(key: String) {
        unlockCallCount++
        locks.remove(key)
    }

    fun reset() {
        locks.clear()
        lockCallCount = 0
        unlockCallCount = 0
    }
}
