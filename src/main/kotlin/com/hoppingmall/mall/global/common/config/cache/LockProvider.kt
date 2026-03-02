package com.hoppingmall.mall.global.common.config.cache

import java.time.Duration

interface LockProvider {
    fun tryLock(key: String, leaseTime: Duration): Boolean
    fun unlock(key: String)
}
