package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.util.concurrent.ConcurrentHashMap

class TwoLevelCacheManager(
    private val redisCacheManager: CacheManager,
    private val policies: Map<String, CachePolicy>,
    private val lockProvider: LockProvider,
    private val hotKeyDetectorRegistry: HotKeyDetectorRegistry
) : CacheManager {

    private val cacheMap = ConcurrentHashMap<String, Cache>()

    override fun getCache(name: String): Cache? {
        val existing = cacheMap[name]
        if (existing != null) return existing

        val redisCache = redisCacheManager.getCache(name) ?: return null
        val policy = policies[name] ?: return redisCache

        val caffeineCache = Caffeine.newBuilder()
            .maximumSize(policy.l1MaxSize)
            .expireAfterWrite(policy.l1Ttl)
            .build<Any, Any>()

        val detector = hotKeyDetectorRegistry.getDetector(name)
        val shardedRedisCache = if (policy.dynamicHotKeyEnabled) {
            ShardedRedisCache(redisCache, policy.hotKeyShardCount)
        } else null

        val twoLevelCache = TwoLevelCache(
            name, caffeineCache, redisCache, policy, lockProvider,
            shardedRedisCache, detector
        )
        cacheMap[name] = twoLevelCache
        return twoLevelCache
    }

    override fun getCacheNames(): Collection<String> {
        return redisCacheManager.cacheNames
    }
}
