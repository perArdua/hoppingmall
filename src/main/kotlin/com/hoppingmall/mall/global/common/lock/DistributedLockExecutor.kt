package com.hoppingmall.mall.global.common.lock

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class DistributedLockExecutor(
    private val redissonClient: RedissonClient,
    private val transactionManager: PlatformTransactionManager
) {

    fun <T> withLock(
        key: String,
        waitTime: Duration = Duration.ofSeconds(3),
        leaseTime: Duration = Duration.ofSeconds(5),
        action: () -> T
    ): T {
        val lock = redissonClient.getLock(key)
        val acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS)
        if (!acquired) {
            throw DistributedLockException()
        }
        try {
            return TransactionTemplate(transactionManager).execute { action() }!!
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
