package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.redisson.api.RBatch
import org.redisson.api.RedissonClient
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("캐시 모듈 스레드 풀 종료")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CacheExecutorShutdownTest {

    @Configuration
    class CacheManagerConfig {
        @Bean
        fun hotKeyDetectorRegistry(): HotKeyDetectorRegistry = HotKeyDetectorRegistry(listOf(hotPolicy))

        @Bean
        fun cacheManager(hotKeyDetectorRegistry: HotKeyDetectorRegistry): CacheManager {
            val redisCacheManager = mock<CacheManager>()
            whenever(redisCacheManager.getCache(CACHE_NAME)).thenReturn(mock<Cache>())
            return TwoLevelCacheManager(
                redisCacheManager = redisCacheManager,
                policies = mapOf(CACHE_NAME to hotPolicy),
                hotKeyDetectorRegistry = hotKeyDetectorRegistry
            )
        }
    }

    @Configuration
    class RedisDetectorConfig {
        @Bean
        fun redissonClient(): RedissonClient = mock<RedissonClient>().also { client ->
            whenever(client.createBatch()).thenAnswer {
                flushStarted.countDown()
                try {
                    flushRelease.await()
                } catch (e: InterruptedException) {
                    flushInterrupted.set(true)
                    throw e
                }
                mock<RBatch>()
            }
        }

        @Bean
        fun hotKeyDetectorRegistry(redissonClient: RedissonClient): HotKeyDetectorRegistry = HotKeyDetectorRegistry(
            policies = listOf(hotPolicy.copy(hotKeyWindow = Duration.ofMillis(200))),
            detectorType = "redis",
            redissonClient = redissonClient
        )
    }

    @Test
    fun 컨텍스트를_닫으면_진행_중인_조기_갱신이_멈추고_닫기가_끝난다() {
        val context = AnnotationConfigApplicationContext(CacheManagerConfig::class.java)
        val cache = context.getBean(CacheManager::class.java).getCache(CACHE_NAME)!!
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        cache.put("1", "old")

        try {
            cache.get("1") {
                started.countDown()
                try {
                    release.await()
                } catch (e: InterruptedException) {
                    interrupted.set(true)
                    throw e
                }
                "new"
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

            context.close()

            assertThat(interrupted.get()).isTrue()
        } finally {
            release.countDown()
            context.close()
        }
    }

    @Test
    fun 컨텍스트를_닫은_뒤에는_조기_갱신을_새로_돌리지_않는다() {
        val context = AnnotationConfigApplicationContext(CacheManagerConfig::class.java)
        val cache = context.getBean(CacheManager::class.java).getCache(CACHE_NAME)!!
        val loads = AtomicInteger(0)
        val loaded = CountDownLatch(1)
        cache.put("1", "old")

        context.close()
        val value = cache.get("1") {
            loads.incrementAndGet()
            loaded.countDown()
            "new"
        }

        assertThat(value).isEqualTo("old")
        assertThat(loaded.await(1, TimeUnit.SECONDS)).isFalse()
        assertThat(loads.get()).isZero()
    }

    @Test
    fun 컨텍스트를_닫으면_진행_중인_핫키_집계가_멈추고_닫기가_끝난다() {
        flushStarted = CountDownLatch(1)
        flushRelease = CountDownLatch(1)
        flushInterrupted.set(false)
        val context = AnnotationConfigApplicationContext(RedisDetectorConfig::class.java)

        try {
            context.getBean(HotKeyDetectorRegistry::class.java).getDetector(CACHE_NAME)!!.recordAccess("1")
            assertThat(flushStarted.await(5, TimeUnit.SECONDS)).isTrue()

            context.close()

            assertThat(flushInterrupted.get()).isTrue()
        } finally {
            flushRelease.countDown()
            context.close()
        }
    }

    companion object {
        private const val CACHE_NAME = "product"

        private val hotPolicy = CachePolicy(
            cacheName = CACHE_NAME,
            l1MaxSize = 100,
            l1Ttl = Duration.ofMinutes(1),
            l2Ttl = Duration.ZERO,
            hotKeyThreshold = 1
        )

        @Volatile
        private var flushStarted = CountDownLatch(1)

        @Volatile
        private var flushRelease = CountDownLatch(1)

        private val flushInterrupted = AtomicBoolean(false)
    }
}
