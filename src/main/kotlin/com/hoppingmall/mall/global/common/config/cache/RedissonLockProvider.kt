package com.hoppingmall.mall.global.common.config.cache

import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.concurrent.TimeUnit

class RedissonLockProvider(
    private val redissonClient: RedissonClient
) : LockProvider {

    override fun tryLock(key: String, leaseTime: Duration): Boolean {
        val lock = redissonClient.getLock(key)
        return lock.tryLock(0, leaseTime.toMillis(), TimeUnit.MILLISECONDS)
    }

    override fun unlock(key: String) {
        val lock = redissonClient.getLock(key)
        if (lock.isHeldByCurrentThread) {
            lock.unlock()
        }
    }
}
