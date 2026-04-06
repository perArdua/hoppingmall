package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.util.concurrent.ConcurrentHashMap

class TwoLevelCacheManager(
    private val redisCacheManager: CacheManager,
    private val policies: Map<String, CachePolicy>,
    private val lockProvider: LockProvider,
    private val hotKeyDetectorRegistry: HotKeyDetectorRegistry,
    private val meterRegistry: MeterRegistry? = null
) : CacheManager {

    private sealed interface CacheLookupResult {
        data class Present(val cache: Cache) : CacheLookupResult
        data class Missing(val expireAt: Long) : CacheLookupResult
    }

    private val cacheMap = ConcurrentHashMap<String, CacheLookupResult>()

    override fun getCache(name: String): Cache? {
        val existing = cacheMap[name]
        if (existing is CacheLookupResult.Missing) {
            return if (System.currentTimeMillis() < existing.expireAt) {
                null
            } else {
                cacheMap.remove(name, existing)
                getCache(name)
            }
        }

        val lookupResult = cacheMap.computeIfAbsent(name) { cacheName ->
            val redisCache = redisCacheManager.getCache(cacheName)
                ?: return@computeIfAbsent CacheLookupResult.Missing(
                    System.currentTimeMillis() + MISSING_CACHE_TTL_MS
                )
            val policy = policies[cacheName]
                ?: return@computeIfAbsent CacheLookupResult.Present(redisCache)

            val caffeineCache = Caffeine.newBuilder()
                .maximumSize(policy.l1MaxSize)
                .expireAfterWrite(policy.l1Ttl)
                .build<Any, Any>()

            val detector = hotKeyDetectorRegistry.getDetector(cacheName)
            val shardedRedisCache = if (policy.dynamicHotKeyEnabled) {
                ShardedRedisCache(redisCache, policy.hotKeyShardCount)
            } else null

            CacheLookupResult.Present(
                TwoLevelCache(
                cacheName, caffeineCache, redisCache, policy, lockProvider,
                shardedRedisCache, detector, meterRegistry
            )
            )
        }

        return when (lookupResult) {
            is CacheLookupResult.Present -> lookupResult.cache
            is CacheLookupResult.Missing -> null
        }
    }

    companion object {
        private const val MISSING_CACHE_TTL_MS = 30_000L
    }

    override fun getCacheNames(): Collection<String> {
        return redisCacheManager.cacheNames
    }
}
