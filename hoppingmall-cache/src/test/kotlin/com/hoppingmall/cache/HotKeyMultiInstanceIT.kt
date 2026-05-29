package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import redis.embedded.RedisServer
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 핵심 주장의 실 Redis 관측: 멀티 인스턴스에서
 *  (1) redis 디텍터가 인스턴스별 카운트를 ZSET으로 전역 합산해 isHot을 승격(local이면 각 1/N이라 미달).
 *  (2) 그 핫키가 논리 만료되면 인스턴스 간 가드(SET NX)로 단 한 인스턴스만 갱신(로더 1회)하고,
 *      다른 인스턴스는 공유 L2에서 그 값을 읽는다.
 *
 * embedded redis 미기동 시 assumeTrue로 skip.
 */
@DisplayName("핫키 멀티 인스턴스 (redis 디텍터 + 가드) 실 Redis 관측")
@DisplayNameGeneration(ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HotKeyMultiInstanceIT {

    private var redisServer: RedisServer? = null
    private var up = false
    private lateinit var redisson: RedissonClient
    private lateinit var connectionFactory: LettuceConnectionFactory

    private val schedulers = mutableListOf<ScheduledExecutorService>()
    private val sameThread = Executor { it.run() }

    @BeforeAll
    fun start() {
        try {
            val port = java.net.ServerSocket(0).use { it.localPort }
            redisServer = RedisServer(port).also { it.start() }
            redisson = Redisson.create(Config().apply { useSingleServer().address = "redis://127.0.0.1:$port" })
            connectionFactory = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port)).apply {
                afterPropertiesSet(); start()
            }
            up = true
        } catch (e: Exception) {
            up = false
        }
    }

    @AfterAll
    fun stop() {
        schedulers.forEach { runCatching { it.shutdownNow() } }
        if (::connectionFactory.isInitialized) runCatching { connectionFactory.destroy() }
        if (::redisson.isInitialized) runCatching { redisson.shutdown() }
        runCatching { redisServer?.stop() }
    }

    // 자동 flush가 테스트에 끼지 않도록 큰 간격으로 두고 flush()는 수동 호출.
    private fun detector(window: Long = 60_000L): RedisHotKeyDetector {
        val sched = Executors.newScheduledThreadPool(1) { r -> Thread(r).apply { isDaemon = true } }
        schedulers.add(sched)
        return RedisHotKeyDetector("product", threshold = 50, windowMs = window, redissonClient = redisson,
            scheduler = sched, flushIntervalMs = 3_600_000L)
    }

    @Test
    fun redis_디텍터는_인스턴스별_카운트를_전역_합산해_isHot을_승격한다() {
        assumeTrue(up, "embedded redis 미기동 → skip")
        val a = detector()
        val b = detector()

        repeat(30) { a.recordAccess("k") } // 인스턴스 A: 30 (local 임계치 50 미달)
        repeat(30) { b.recordAccess("k") } // 인스턴스 B: 30 (미달)

        assertThat(a.isHot("k")).isFalse() // flush 전: 스냅샷 비어있음
        // flush로 ZSET에 합산(30+30=60) 후 양쪽이 합산본을 읽도록 교차 flush
        a.flush(); b.flush(); a.flush(); b.flush()

        assertThat(a.isHot("k")).isTrue() // 전역 60 >= 50 → 양쪽 모두 hot (local이었다면 30<50 → false)
        assertThat(b.isHot("k")).isTrue()
    }

    @Test
    fun 핫키_논리만료시_가드로_한_인스턴스만_갱신하고_다른_인스턴스는_공유L2에서_읽는다() {
        assumeTrue(up, "embedded redis 미기동 → skip")
        val policy = CachePolicy(
            cacheName = "product", l1MaxSize = 100,
            l1Ttl = Duration.ofSeconds(600), l2Ttl = Duration.ofMillis(1000),
            hotKeyThreshold = 50, valueType = CacheValueSerializer.entryOf(String::class.java)
        )
        val sharedL2 = TwoLevelCacheManager
            .buildRedisCacheManager(connectionFactory, mapOf("product" to policy))
            .apply { afterPropertiesSet() }
            .getCache("product")!!

        val now = AtomicLong(0L)
        val loadCount = AtomicInteger(0)
        val reg = SimpleMeterRegistry()

        val detA = detector(); val detB = detector()
        // 두 인스턴스 모두 hot으로 승격
        repeat(30) { detA.recordAccess("k") }; repeat(30) { detB.recordAccess("k") }
        detA.flush(); detB.flush(); detA.flush(); detB.flush()
        assertThat(detA.isHot("k")).isTrue(); assertThat(detB.isHot("k")).isTrue()

        fun instance(guard: RefreshGuard, det: RedisHotKeyDetector) = TwoLevelCache(
            name = "product",
            caffeineCache = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofSeconds(600)).build(),
            redisCache = sharedL2, policy = policy,
            refreshExecutor = sameThread, refreshGuard = guard, hotKeyDetector = det,
            meterRegistry = reg, clock = { now.get() }, random = { 1e-6 }
        )
        val cacheA = instance(RedissonRefreshGuard(redisson), detA) // 인스턴스 A
        val cacheB = instance(RedissonRefreshGuard(redisson), detB) // 인스턴스 B (다른 instanceId, 같은 redis)

        // 콜드 적재(A): physicalExpireAt = 100 + 1000 = 1100
        now.set(0L)
        cacheA.get("k", Callable { loadCount.incrementAndGet(); now.addAndGet(100L); "v1" })
        assertThat(loadCount.get()).isEqualTo(1)

        // 논리 만료(now=600): A가 먼저 갱신(가드 획득) → 로더 1회, 공유 L2에 기록
        now.set(600L)
        cacheA.get("k", Callable { loadCount.incrementAndGet(); now.addAndGet(50L); "v2" })
        // B도 만료 트리거하지만 가드 SET NX 패배 → 로드 안 하고 공유 L2에서 읽음
        val bValue = cacheB.get("k", Callable { loadCount.incrementAndGet(); "v2" })

        assertThat(loadCount.get()).isEqualTo(2) // 콜드1 + A 갱신1. B는 가드 패배로 미로드(인스턴스 간 dedup)
        assertThat(bValue).isEqualTo("v2")        // B는 공유 L2에서 A가 갱신한 값을 읽음
    }
}
