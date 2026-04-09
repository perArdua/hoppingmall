package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

@DisplayName("TwoLevelCacheManager 추가 경로")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheManagerExtraTest {

    private fun createManager(
        redisCacheManager: CacheManager,
        vararg policies: CachePolicy
    ): TwoLevelCacheManager {
        return TwoLevelCacheManager(
            redisCacheManager = redisCacheManager,
            policies = policies.associateBy { it.cacheName },
            lockProvider = FakeLockProvider(),
            hotKeyDetectorRegistry = HotKeyDetectorRegistry(policies.toList())
        )
    }

    private fun policy(name: String, hotKeyThreshold: Long = 0L) = CachePolicy(
        cacheName = name,
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(10),
        l2Ttl = Duration.ofMinutes(1),
        hotKeyThreshold = hotKeyThreshold
    )

    @Test
    fun getCacheNames는_redisCacheManager의_캐시명_목록을_반환한다() {
        val redisCacheManager = mock<CacheManager>()
        whenever(redisCacheManager.cacheNames).thenReturn(listOf("product", "order"))
        val manager = createManager(redisCacheManager)

        assertThat(manager.cacheNames).containsExactlyInAnyOrder("product", "order")
    }

    @Test
    fun 정책이_없는_캐시명은_redis_캐시를_그대로_반환한다() {
        val redisCacheManager = mock<CacheManager>()
        val redisCache = mock<Cache>()
        whenever(redisCacheManager.getCache("no-policy-cache")).thenReturn(redisCache)
        val manager = createManager(redisCacheManager)

        val result = manager.getCache("no-policy-cache")

        assertThat(result).isSameAs(redisCache)
    }

    @Test
    fun hotKey_비활성_정책은_ShardedRedisCache_없이_TwoLevelCache를_생성한다() {
        val redisCacheManager = mock<CacheManager>()
        val redisCache = mock<Cache>()
        val pol = policy("simple-cache", hotKeyThreshold = 0L)
        whenever(redisCacheManager.getCache("simple-cache")).thenReturn(redisCache)
        val manager = createManager(redisCacheManager, pol)

        val result = manager.getCache("simple-cache")

        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(TwoLevelCache::class.java)
    }

    @Test
    fun hotKey_활성_정책은_TwoLevelCache를_생성한다() {
        val redisCacheManager = mock<CacheManager>()
        val redisCache = mock<Cache>()
        val pol = policy("hot-cache", hotKeyThreshold = 5L)
        whenever(redisCacheManager.getCache("hot-cache")).thenReturn(redisCache)
        val manager = createManager(redisCacheManager, pol)

        val result = manager.getCache("hot-cache")

        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(TwoLevelCache::class.java)
    }

    @Test
    fun 동일_캐시명_두_번_조회_시_같은_인스턴스를_반환한다() {
        val redisCacheManager = mock<CacheManager>()
        val redisCache = mock<Cache>()
        val pol = policy("product")
        whenever(redisCacheManager.getCache("product")).thenReturn(redisCache)
        val manager = createManager(redisCacheManager, pol)

        val first = manager.getCache("product")
        val second = manager.getCache("product")

        assertThat(first).isSameAs(second)
    }

    @Test
    fun buildRedisCacheManager_커스텀_TTL로_RedisCacheManager를_생성한다() {
        val connectionFactory = mock<RedisConnectionFactory>()
        val policies = mapOf(
            "product" to policy("product"),
            "order" to policy("order")
        )

        val redisCacheManager = TwoLevelCacheManager.buildRedisCacheManager(
            connectionFactory,
            policies,
            Duration.ofMinutes(10)
        )

        assertThat(redisCacheManager).isNotNull()
        assertThat(redisCacheManager).isInstanceOf(org.springframework.data.redis.cache.RedisCacheManager::class.java)
    }

    @Test
    fun buildRedisCacheManager_기본_TTL로_생성된다() {
        val connectionFactory = mock<RedisConnectionFactory>()

        val redisCacheManager = TwoLevelCacheManager.buildRedisCacheManager(
            connectionFactory,
            emptyMap()
        )

        assertThat(redisCacheManager).isNotNull()
        assertThat(redisCacheManager).isInstanceOf(org.springframework.data.redis.cache.RedisCacheManager::class.java)
    }
}
