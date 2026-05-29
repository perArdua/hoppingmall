package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
import java.util.concurrent.Executor

@DisplayName("TwoLevelCache")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheTest {

    private val redisCache: Cache = mock()
    private lateinit var meterRegistry: SimpleMeterRegistry

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
        meterRegistry = SimpleMeterRegistry()
    }

    private fun createCache(policy: CachePolicy, detector: HotKeyDetector? = null): TwoLevelCache {
        val caffeineCache = Caffeine.newBuilder()
            .maximumSize(policy.l1MaxSize)
            .expireAfterWrite(policy.l1Ttl)
            .build<Any, Any>()
        return TwoLevelCache(
            name = policy.cacheName,
            caffeineCache = caffeineCache,
            redisCache = redisCache,
            policy = policy,
            hotKeyDetector = detector,
            meterRegistry = meterRegistry
        )
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
            assertEquals(1.0, meterRegistry.counter("cache.l1.hit", "cache", "category").count())
        }

        @Test
        fun valueLoader_경로에서도_L1_히트_메트릭이_증가한다() {
            val cache = createCache(normalPolicy)

            cache.put(1L, "cached-value")

            val result = cache.get(1L, Callable { "should-not-call" })

            assertEquals("cached-value", result)
            assertEquals(1.0, meterRegistry.counter("cache.l1.hit", "cache", "category").count())
        }
    }

    @Nested
    @DisplayName("L2 캐시 히트 및 미스")
    inner class L2HitAndMiss {

        @Test
        fun L2에_값이_있으면_반환하고_L1에_승격한다() {
            val cache = createCache(normalPolicy)
            val wrapper: Cache.ValueWrapper = mock()
            whenever(wrapper.get()).thenReturn("redis-value")
            whenever(redisCache.get(1L)).thenReturn(wrapper)

            val result = cache.get(1L, Callable { "should-not-call" })

            assertEquals("redis-value", result)
            assertEquals(1.0, meterRegistry.counter("cache.l2.hit", "cache", "category").count())

            // L1 승격 확인: 두 번째 조회는 L2를 다시 보지 않는다.
            cache.get(1L, Callable { "should-not-call" })
            verify(redisCache).get(1L)
        }

        @Test
        fun L1_L2_모두_미스이면_로더로_적재한다() {
            val cache = createCache(normalPolicy)
            whenever(redisCache.get(1L)).thenReturn(null)

            val result = cache.get(1L, Callable { "loaded" })

            assertEquals("loaded", result)
            assertEquals(1.0, meterRegistry.counter("cache.miss", "cache", "category").count())
        }

        @Test
        fun L1_미스_시_핫키_탐지기에_접근을_기록한다() {
            val detector = FakeHotKeyDetector()
            val cache = createCache(hotKeyPolicy, detector)
            whenever(redisCache.get(1L)).thenReturn(null)

            cache.get(1L, Callable { "loaded" })

            assertEquals(1, detector.recordCount)
            assertEquals(1.0, meterRegistry.counter("cache.miss", "cache", "product").count())
        }
    }

    @Nested
    @DisplayName("쓰기 및 무효화")
    inner class WriteAndEvict {

        @Test
        fun put은_L1과_L2에_저장한다() {
            val cache = createCache(normalPolicy)

            cache.put(1L, "value")

            verify(redisCache).put(1L, "value")
            assertEquals("value", cache.get(1L, Callable { "should-not-call" }))
        }

        @Test
        fun evict은_L1과_L2에서_삭제한다() {
            val cache = createCache(normalPolicy)
            cache.put(1L, "value")

            cache.evict(1L)

            verify(redisCache).evict(1L)
            whenever(redisCache.get(1L)).thenReturn(null)
            assertEquals("reloaded", cache.get(1L, Callable { "reloaded" }))
        }

        @Test
        fun 비핫_캐시는_CacheEntry로_래핑하지_않는다() {
            val cache = createCache(normalPolicy)

            cache.put(1L, "value")

            val stored = cache.nativeCache
                .let { it as com.github.benmanes.caffeine.cache.Cache<Any, Any> }
                .getIfPresent(1L)
            assertEquals("value", stored)
        }

        @Test
        fun 기본_clock_random으로_핫_캐시_재조회가_정상_동작한다() {
            // clock/random 미주입 → 기본 람다(System::currentTimeMillis, ThreadLocalRandom) 경로 실행.
            val caffeine = Caffeine.newBuilder()
                .maximumSize(hotKeyPolicy.l1MaxSize)
                .expireAfterWrite(hotKeyPolicy.l1Ttl)
                .build<Any, Any>()
            val cache = TwoLevelCache(
                name = hotKeyPolicy.cacheName,
                caffeineCache = caffeine,
                redisCache = redisCache,
                policy = hotKeyPolicy,
                refreshExecutor = Executor { it.run() },
                meterRegistry = meterRegistry
            )
            whenever(redisCache.get(1L)).thenReturn(null)

            cache.put(1L, "value")
            // 물리 만료가 한참 남아 있어 shouldRefresh는 거짓이지만 random 기본 람다는 평가됨.
            val result = cache.get(1L, Callable { "reloaded" })

            assertEquals("value", result)
        }

        @Test
        fun 핫_캐시는_비null_값을_CacheEntry로_래핑한다() {
            val cache = createCache(hotKeyPolicy)
            whenever(redisCache.get(1L)).thenReturn(null)

            cache.put(1L, "value")

            val stored = cache.nativeCache
                .let { it as com.github.benmanes.caffeine.cache.Cache<Any, Any> }
                .getIfPresent(1L)
            assert(stored is CacheEntry<*>) { "핫 캐시는 CacheEntry 래핑 기대, got $stored" }
            assertEquals("value", (stored as CacheEntry<*>).value)
        }
    }
}
