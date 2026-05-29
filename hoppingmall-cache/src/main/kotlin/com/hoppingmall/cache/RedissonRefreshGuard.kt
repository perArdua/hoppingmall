package com.hoppingmall.cache

import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * raw SET NX PX 기반 갱신 가드. instanceId는 per-JVM UUID(로깅/소유 식별용).
 */
class RedissonRefreshGuard(
    private val redissonClient: RedissonClient,
    val instanceId: String = UUID.randomUUID().toString()
) : RefreshGuard {

    override fun tryAcquire(key: String, ttl: Duration): Boolean =
        redissonClient.getBucket<String>(key)
            .trySet(instanceId, ttl.toMillis(), TimeUnit.MILLISECONDS)

    override fun markFailed(key: String, cooldown: Duration) {
        redissonClient.getBucket<String>(key)
            .set(instanceId, cooldown.toMillis(), TimeUnit.MILLISECONDS)
    }
}
