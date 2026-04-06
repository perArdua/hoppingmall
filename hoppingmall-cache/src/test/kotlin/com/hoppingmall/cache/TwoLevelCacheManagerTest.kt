package com.hoppingmall.cache

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("TwoLevelCacheManager")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class TwoLevelCacheManagerTest {

    private fun createManager(redisCacheManager: CacheManager, vararg policies: CachePolicy): TwoLevelCacheManager {
        return TwoLevelCacheManager(
            redisCacheManager = redisCacheManager,
            policies = policies.associateBy { it.cacheName },
            lockProvider = FakeLockProvider(),
            hotKeyDetectorRegistry = HotKeyDetectorRegistry(policies.toList()),
            meterRegistry = SimpleMeterRegistry()
        )
    }

    private fun defaultPolicy(name: String = "product") = CachePolicy(
        cacheName = name,
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(10),
        l2Ttl = Duration.ofMinutes(1),
        hotKeyThreshold = 5
    )

    @Suppress("UNCHECKED_CAST")
    private fun injectExpiredMissing(manager: TwoLevelCacheManager, cacheName: String) {
        val field = TwoLevelCacheManager::class.java.getDeclaredField("cacheMap")
        field.isAccessible = true
        val cacheMap = field.get(manager) as ConcurrentHashMap<String, Any>
        val missingClass = Class.forName("com.hoppingmall.cache.TwoLevelCacheManager\$CacheLookupResult\$Missing")
        val constructor = missingClass.getDeclaredConstructor(Long::class.java)
        constructor.isAccessible = true
        val expiredMissing = constructor.newInstance(System.currentTimeMillis() - 1L)
        cacheMap[cacheName] = expiredMissing
    }

    @Test
    fun getCache_동시_호출_시_하나의_인스턴스만_생성한다() {
        val redisCacheManager = mock<CacheManager>()
        val redisCache = mock<Cache>()
        val policy = defaultPolicy()
        val manager = createManager(redisCacheManager, policy)

        whenever(redisCacheManager.getCache("product")).thenAnswer {
            Thread.sleep(50)
            redisCache
        }

        val threadCount = 16
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val results = mutableListOf<Cache?>()
        val resultsLock = Any()

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val cache = manager.getCache("product")
                    synchronized(resultsLock) {
                        results += cache
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        assertThat(results).hasSize(threadCount)
        assertThat(results.filterNotNull().distinct()).hasSize(1)
        verify(redisCacheManager, times(1)).getCache("product")
    }

    @Test
    fun Redis_다운_시_첫_호출은_null을_반환하고_30초_내_재호출도_Redis를_재시도하지_않는다() {
        val redisCacheManager = mock<CacheManager>()
        val policy = defaultPolicy()
        val manager = createManager(redisCacheManager, policy)

        whenever(redisCacheManager.getCache("product")).thenReturn(null)

        val first = manager.getCache("product")
        val second = manager.getCache("product")

        assertThat(first).isNull()
        assertThat(second).isNull()
        verify(redisCacheManager, times(1)).getCache("product")
    }

    @Test
    fun TTL_만료_후_Redis를_재시도한다() {
        val redisCacheManager = mock<CacheManager>()
        val redisCache = mock<Cache>()
        val policy = defaultPolicy()
        val manager = createManager(redisCacheManager, policy)

        injectExpiredMissing(manager, "product")

        whenever(redisCacheManager.getCache("product")).thenReturn(redisCache)

        val result = manager.getCache("product")

        assertThat(result).isNotNull()
        verify(redisCacheManager, times(1)).getCache("product")
    }

    @Test
    fun Redis_정상_동작_시_캐시_인스턴스를_반환한다() {
        val redisCacheManager = mock<CacheManager>()
        val redisCache = mock<Cache>()
        val policy = defaultPolicy()
        val manager = createManager(redisCacheManager, policy)

        whenever(redisCacheManager.getCache("product")).thenReturn(redisCache)

        val result = manager.getCache("product")

        assertThat(result).isNotNull()
        verify(redisCacheManager, times(1)).getCache("product")
    }
}
