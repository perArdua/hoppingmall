package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class TwoLevelCacheManager(
    private val redisCacheManager: CacheManager,
    private val policies: Map<String, CachePolicy>,
    private val hotKeyDetectorRegistry: HotKeyDetectorRegistry,
    private val refreshGuard: RefreshGuard? = null,
    private val meterRegistry: MeterRegistry? = null
) : CacheManager {

    // XFetch 비동기 갱신 전용 풀(탐지 스케줄러와 격리). 큐 한정 + AbortPolicy:
    // 포화 시 RejectedExecutionException을 던져 호출부(maybeTriggerRefresh)가 refreshInFlight를 정리하게 한다.
    // (DiscardPolicy는 조용히 버려서 doRefresh.finally가 안 돌아 키가 영구 잔류 → 그 키 갱신 정지 버그 유발)
    private val refreshExecutor: Executor = ThreadPoolExecutor(
        2, 4, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(64),
        { r -> Thread(r, "cache-xfetch-refresh").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

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

            CacheLookupResult.Present(
                TwoLevelCache(
                    name = cacheName,
                    caffeineCache = caffeineCache,
                    redisCache = redisCache,
                    policy = policy,
                    refreshExecutor = refreshExecutor,
                    refreshGuard = refreshGuard,
                    hotKeyDetector = detector,
                    meterRegistry = meterRegistry
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

        fun buildRedisCacheManager(
            connectionFactory: RedisConnectionFactory,
            policies: Map<String, CachePolicy>,
            defaultTtl: Duration = Duration.ofMinutes(30)
        ): RedisCacheManager {
            // 값 직렬화를 JSON으로 (키는 기본 StringRedisSerializer 유지). JDK 직렬화는 비Serializable 값에서 실패함.
            // 기본 경로: 캐시별 타입드 직렬화(@class 없음). valueType 미선언 캐시는 fallback(@class, 한정 PTV).
            val fallbackPair = SerializationPair.fromSerializer(CacheValueSerializer.fallback())
            val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(defaultTtl)
                .serializeValuesWith(fallbackPair)
            val perCacheConfigs = policies.mapValues { (_, policy) ->
                val valuePair = policy.valueType
                    ?.let { SerializationPair.fromSerializer(TypedCacheValueSerializer(CacheValueSerializer.mapper, it)) }
                    ?: fallbackPair
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(policy.l2Ttl)
                    .serializeValuesWith(valuePair)
            }
            return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfigs)
                .build()
        }
    }

    override fun getCacheNames(): Collection<String> = redisCacheManager.cacheNames
}
