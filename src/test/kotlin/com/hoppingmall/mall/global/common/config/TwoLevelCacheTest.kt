package com.hoppingmall.mall.global.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import com.hoppingmall.mall.global.common.config.cache.CachePolicy
import com.hoppingmall.mall.global.common.config.cache.FakeHotKeyDetector
import com.hoppingmall.mall.global.common.config.cache.FakeLockProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("TwoLevelCache")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheTest {

    private val redisCache: Cache = mock()
    private val lockProvider = FakeLockProvider()

    private val hotKeyPolicy = CachePolicy(
        cacheName = "product",
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(10),
        l2Ttl = Duration.ofMinutes(10),
        jitterPercent = 10,
        hotKeyThreshold = 5L,
        hotKeyShardCount = 4
    )

    private val normalPolicy = CachePolicy(
        cacheName = "category",
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(30),
        l2Ttl = Duration.ofMinutes(5),
        jitterPercent = 10
    )

    @BeforeEach
    fun setUp() {
        lockProvider.reset()
    }

    private fun createCache(policy: CachePolicy): TwoLevelCache {
        val caffeineCache = Caffeine.newBuilder()
            .maximumSize(policy.l1MaxSize)
            .expireAfterWrite(policy.l1Ttl)
            .build<Any, Any>()
        return TwoLevelCache(policy.cacheName, caffeineCache, redisCache, policy, lockProvider)
    }

    @Nested
    @DisplayName("핫키 분산락")
    inner class HotKeyLock {
        @Test
        fun 핫키_감지_시_락을_사용한다() {
            val fakeDetector = FakeHotKeyDetector()
            fakeDetector.overrideIsHot = true
            val shardedCache: Cache = mock()

            val caffeineCache = Caffeine.newBuilder()
                .maximumSize(hotKeyPolicy.l1MaxSize)
                .expireAfterWrite(hotKeyPolicy.l1Ttl)
                .build<Any, Any>()

            val cache = TwoLevelCache(
                hotKeyPolicy.cacheName, caffeineCache, redisCache, hotKeyPolicy,
                lockProvider, shardedCache, fakeDetector
            )

            whenever(shardedCache.get(1L)).thenReturn(null)
            whenever(redisCache.get(1L)).thenReturn(null)

            cache.get(1L, Callable { "loaded-value" })

            assertEquals(1, lockProvider.lockCallCount)
            assertEquals(1, lockProvider.unlockCallCount)
        }

        @Test
        fun 일반_캐시는_락을_사용하지_않는다() {
            val cache = createCache(normalPolicy)
            val valueLoader = Callable { "loaded-value" }

            whenever(redisCache.get(1L)).thenReturn(null)

            cache.get(1L, valueLoader)

            assertEquals(0, lockProvider.lockCallCount)
        }

        @Test
        fun 핫키_동시_요청_시_DB_로더는_최소_호출된다() {
            val fakeDetector = FakeHotKeyDetector()
            fakeDetector.overrideIsHot = true
            val shardedCache: Cache = mock()

            val caffeineCache = Caffeine.newBuilder()
                .maximumSize(hotKeyPolicy.l1MaxSize)
                .expireAfterWrite(hotKeyPolicy.l1Ttl)
                .build<Any, Any>()

            val cache = TwoLevelCache(
                hotKeyPolicy.cacheName, caffeineCache, redisCache, hotKeyPolicy,
                lockProvider, shardedCache, fakeDetector
            )

            val dbCallCount = AtomicInteger(0)
            val threadCount = 10
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            val valueWrapper: Cache.ValueWrapper = mock()
            whenever(valueWrapper.get()).thenReturn("cached-value")
            whenever(shardedCache.get(1L)).thenReturn(null)

            var firstCall = true
            whenever(redisCache.get(1L)).thenAnswer {
                if (firstCall) {
                    firstCall = false
                    null
                } else {
                    valueWrapper
                }
            }

            repeat(threadCount) {
                executor.submit {
                    try {
                        cache.get(1L, Callable {
                            dbCallCount.incrementAndGet()
                            Thread.sleep(50)
                            "loaded-value"
                        })
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            assert(dbCallCount.get() <= 3) {
                "DB 로더가 ${dbCallCount.get()}회 호출됨 (3회 이하 기대)"
            }
        }
    }

    @Nested
    @DisplayName("L1 캐시 히트")
    inner class L1CacheHit {
        @Test
        fun L1에_값이_있으면_L2를_조회하지_않는다() {
            val cache = createCache(normalPolicy)

            cache.put(1L, "cached-value")

            val result = cache.get(1L, Callable { "should-not-call" })

            assertEquals("cached-value", result)
            verify(redisCache, never()).get(1L)
        }
    }

    @Nested
    @DisplayName("핫키 동적 감지")
    inner class HotKeyDynamicDetection {

        private val fakeDetector = FakeHotKeyDetector()
        private val shardedCache: Cache = mock()

        private fun createHotKeyCache(): TwoLevelCache {
            val caffeineCache = Caffeine.newBuilder()
                .maximumSize(hotKeyPolicy.l1MaxSize)
                .expireAfterWrite(hotKeyPolicy.l1Ttl)
                .build<Any, Any>()
            return TwoLevelCache(
                hotKeyPolicy.cacheName, caffeineCache, redisCache, hotKeyPolicy,
                lockProvider, shardedCache, fakeDetector
            )
        }

        @BeforeEach
        fun setUp() {
            fakeDetector.reset()
        }

        @Test
        fun 핫키로_판별되면_샤딩된_캐시에서_조회한다() {
            fakeDetector.overrideIsHot = true
            val cache = createHotKeyCache()

            val valueWrapper: Cache.ValueWrapper = mock()
            whenever(valueWrapper.get()).thenReturn("shard-value")
            whenever(shardedCache.get(any())).thenReturn(valueWrapper)

            val result = cache.get(1L, Callable { "should-not-call" })

            assertEquals("shard-value", result)
        }

        @Test
        fun 콜드키는_일반_Redis_캐시에서_조회한다() {
            fakeDetector.overrideIsHot = false
            val cache = createHotKeyCache()

            val valueWrapper: Cache.ValueWrapper = mock()
            whenever(valueWrapper.get()).thenReturn("redis-value")
            whenever(redisCache.get(1L)).thenReturn(valueWrapper)

            val result = cache.get(1L, Callable { "should-not-call" })

            assertEquals("redis-value", result)
            verify(shardedCache, never()).get(any())
        }

        @Test
        fun L1_미스_시_접근을_기록한다() {
            fakeDetector.overrideIsHot = false
            val cache = createHotKeyCache()

            whenever(redisCache.get(1L)).thenReturn(null)

            cache.get(1L, Callable { "loaded" })

            assertEquals(1, fakeDetector.recordCount)
        }

        @Test
        fun 핫키_evict은_샤딩된_캐시에서_삭제한다() {
            fakeDetector.overrideIsHot = true
            val cache = createHotKeyCache()

            cache.evict(1L)

            verify(shardedCache).evict(1L)
            verify(redisCache, never()).evict(any())
        }

        @Test
        fun 콜드키_evict은_일반_캐시에서_삭제한다() {
            fakeDetector.overrideIsHot = false
            val cache = createHotKeyCache()

            cache.evict(1L)

            verify(redisCache).evict(1L)
            verify(shardedCache, never()).evict(any())
        }

        @Test
        fun 핫키_put은_샤딩된_캐시에_저장한다() {
            fakeDetector.overrideIsHot = true
            val cache = createHotKeyCache()

            cache.put(1L, "value")

            verify(shardedCache).put(1L, "value")
            verify(redisCache, never()).put(any(), any())
        }
    }
}
