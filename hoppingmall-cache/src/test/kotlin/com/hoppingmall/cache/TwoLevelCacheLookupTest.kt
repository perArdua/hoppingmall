package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
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

@DisplayName("TwoLevelCache 조회 및 부가 경로")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheLookupTest {

    private val redisCache: Cache = mock()
    private lateinit var meterRegistry: SimpleMeterRegistry

    private val normalPolicy = CachePolicy(
        cacheName = "category",
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(30),
        l2Ttl = Duration.ofMinutes(5),
        jitterPercent = 10
    )

    private val hotKeyPolicy = CachePolicy(
        cacheName = "product",
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(10),
        l2Ttl = Duration.ofMinutes(10),
        jitterPercent = 10,
        hotKeyThreshold = 5L,
        hotKeyShardCount = 4
    )

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
    }

    private fun createCache(policy: CachePolicy): TwoLevelCache {
        val caffeineCache = Caffeine.newBuilder()
            .maximumSize(policy.l1MaxSize)
            .expireAfterWrite(policy.l1Ttl)
            .build<Any, Any>()
        return TwoLevelCache(
            name = policy.cacheName,
            caffeineCache = caffeineCache,
            redisCache = redisCache,
            policy = policy,
            meterRegistry = meterRegistry
        )
    }

    @Nested
    @DisplayName("lookup 경로")
    inner class LookupPath {

        @Test
        fun L1_히트_시_L2를_조회하지_않고_값을_반환한다() {
            val cache = createCache(normalPolicy)
            cache.put("k1", "v1")

            val result = cache.get("k1")

            assertThat(result?.get()).isEqualTo("v1")
            verify(redisCache, never()).get(any<Any>())
            assertThat(meterRegistry.counter("cache.l1.hit", "cache", "category").count()).isEqualTo(1.0)
        }

        @Test
        fun L2_히트_시_값을_반환하고_L1에_승격한다() {
            val cache = createCache(normalPolicy)
            val wrapper: Cache.ValueWrapper = mock()
            whenever(wrapper.get()).thenReturn("redis-val")
            whenever(redisCache.get("k1")).thenReturn(wrapper)

            val result = cache.get("k1")

            assertThat(result?.get()).isEqualTo("redis-val")
            assertThat(meterRegistry.counter("cache.l2.hit", "cache", "category").count()).isEqualTo(1.0)
        }

        @Test
        fun L1_L2_모두_미스이면_null을_반환하고_miss_메트릭이_증가한다() {
            val cache = createCache(normalPolicy)
            whenever(redisCache.get(any<Any>())).thenReturn(null)

            val result = cache.get("missing")

            assertThat(result).isNull()
            assertThat(meterRegistry.counter("cache.miss", "cache", "category").count()).isEqualTo(1.0)
        }

        @Test
        fun lookup은_핫_캐시여도_CacheEntry를_언래핑하여_반환한다() {
            val cache = createCache(hotKeyPolicy)
            cache.put("k1", "v1") // 핫 정책이므로 CacheEntry로 래핑되어 저장됨

            val result = cache.get("k1")

            assertThat(result?.get()).isEqualTo("v1")
        }

        @Test
        fun getName은_cacheName을_반환한다() {
            val cache = createCache(normalPolicy)
            assertThat(cache.name).isEqualTo("category")
        }

        @Test
        fun getNativeCache는_caffeineCache를_반환한다() {
            val cache = createCache(normalPolicy)
            assertThat(cache.nativeCache).isNotNull()
        }
    }

    @Nested
    @DisplayName("put null 값")
    inner class PutNull {

        @Test
        fun null_값을_put하면_이후_조회가_캐시된_null을_반환한다() {
            val cache = createCache(normalPolicy)
            cache.put("k1", "v1")
            cache.put("k1", null)

            val result = cache.get("k1")

            assertThat(result?.get()).isNull()
        }
    }

    @Nested
    @DisplayName("clear")
    inner class ClearOperation {

        @Test
        fun clear_호출_시_L2_clear를_위임하고_L1을_비운다() {
            val cache = createCache(normalPolicy)
            cache.put("k1", "v1")

            cache.clear()

            verify(redisCache).clear()
            whenever(redisCache.get(any<Any>())).thenReturn(null)
            assertThat(cache.get("k1")).isNull()
        }

        @Test
        fun L2_clear_예외_발생_시_L1은_정상_비워진다() {
            val cache = createCache(normalPolicy)
            cache.put("k1", "v1")
            whenever(redisCache.clear()).thenThrow(RuntimeException("redis down"))

            cache.clear()

            whenever(redisCache.get(any<Any>())).thenReturn(null)
            assertThat(cache.get("k1")).isNull()
            assertThat(meterRegistry.counter("cache.l2.failure", "cache", "category").count()).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("evict 예외 처리")
    inner class EvictException {

        @Test
        fun L2_evict_예외_발생_시_L1에서는_삭제된다() {
            val cache = createCache(normalPolicy)
            cache.put("k1", "v1")
            whenever(redisCache.evict(any())).thenThrow(RuntimeException("redis down"))

            cache.evict("k1")

            whenever(redisCache.get(any<Any>())).thenReturn(null)
            assertThat(cache.get("k1")).isNull()
            assertThat(meterRegistry.counter("cache.l2.failure", "cache", "category").count()).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("safeL2 예외 처리")
    inner class SafeL2Exception {

        @Test
        fun L2_get_예외_발생_시_miss_처리하고_valueLoader를_호출한다() {
            val cache = createCache(normalPolicy)
            whenever(redisCache.get(any<Any>())).thenThrow(RuntimeException("redis error"))

            val result = cache.get("k1", Callable { "fallback" })

            assertThat(result).isEqualTo("fallback")
            assertThat(meterRegistry.counter("cache.l2.failure", "cache", "category").count()).isEqualTo(1.0)
        }

        @Test
        fun L2_put_예외_발생_시_메트릭을_증가한다() {
            val cache = createCache(normalPolicy)
            whenever(redisCache.put(any(), any())).thenThrow(RuntimeException("redis error"))
            whenever(redisCache.get(any<Any>())).thenReturn(null)

            cache.get("k1", Callable { "loaded" })

            assertThat(meterRegistry.counter("cache.l2.failure", "cache", "category").count()).isEqualTo(1.0)
        }
    }
}
