package com.hoppingmall.mall.global.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.time.Duration

data class L1CacheSpec(
    val maxSize: Long,
    val ttl: Duration
)

class TwoLevelCacheManager(
    private val redisCacheManager: CacheManager,
    private val l1Specs: Map<String, L1CacheSpec>
) : CacheManager {

    private val cacheMap = mutableMapOf<String, Cache>()

    override fun getCache(name: String): Cache? {
        return cacheMap.getOrPut(name) {
            val redisCache = redisCacheManager.getCache(name) ?: return null

            val spec = l1Specs[name] ?: return redisCache

            val caffeineCache = Caffeine.newBuilder()
                .maximumSize(spec.maxSize)
                .expireAfterWrite(spec.ttl)
                .build<Any, Any>()

            TwoLevelCache(name, caffeineCache, redisCache)
        }
    }

    override fun getCacheNames(): Collection<String> {
        return redisCacheManager.cacheNames
    }
}
