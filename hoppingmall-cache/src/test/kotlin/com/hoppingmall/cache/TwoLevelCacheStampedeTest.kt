package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("TwoLevelCache Stampede Collapse 검증")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheStampedeTest {

    private val redisCache: Cache = mock()
    private val lockProvider = FakeLockProvider()
    private lateinit var meterRegistry: SimpleMeterRegistry

    private val nonHotPolicy = CachePolicy(
        cacheName = "category",
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(30),
        l2Ttl = Duration.ofMinutes(5),
        jitterPercent = 10
    )

    private val shortTtlPolicy = CachePolicy(
        cacheName = "category-short",
        l1MaxSize = 100,
        l1Ttl = Duration.ofMillis(100),
        l2Ttl = Duration.ofMillis(200),
        jitterPercent = 0
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
        lockProvider.reset()
        meterRegistry = SimpleMeterRegistry()
        whenever(redisCache.get(org.mockito.kotlin.any())).thenReturn(null)
    }

    private fun createNonHotCache(policy: CachePolicy = nonHotPolicy): TwoLevelCache {
        val caffeine = Caffeine.newBuilder()
            .maximumSize(policy.l1MaxSize)
            .expireAfterWrite(policy.l1Ttl)
            .build<Any, Any>()
        return TwoLevelCache(
            policy.cacheName, caffeine, redisCache, policy,
            lockProvider, meterRegistry = meterRegistry
        )
    }

    private fun collapsedCount(cacheName: String): Double =
        meterRegistry.counter("cache.singleflight.collapsed", "cache", cacheName).count()

    private fun missCount(cacheName: String): Double =
        meterRegistry.counter("cache.miss", "cache", cacheName).count()

    @Test
    @DisplayName("[1-1] cold start 100 스레드 동시 요청 → loader 1회 호출 + collapsed=99")
    fun cold_start_single_flight_collapse() {
        val cache = createNonHotCache()
        val key = 1001L
        val threadCount = 100
        val loaderCount = AtomicInteger(0)
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val gate = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Any?>()
        val expected = "loaded-value-1001"

        val startedNanos = System.nanoTime()

        repeat(threadCount) {
            executor.submit {
                try {
                    barrier.await()
                    val v = cache.get(key, Callable<String> {
                        loaderCount.incrementAndGet()
                        gate.await()
                        expected
                    })
                    results.add(v)
                } finally {
                    latch.countDown()
                }
            }
        }

        Thread.sleep(150)
        gate.countDown()
        assertTrue(latch.await(10, TimeUnit.SECONDS), "all threads finished")
        executor.shutdown()

        val elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000

        assertEquals(1, loaderCount.get(), "loader 호출은 정확히 1회 (collapse)")
        assertEquals(threadCount, results.size, "100 스레드 모두 결과 수신")
        assertEquals(setOf<Any?>(expected), results.toSet(), "전부 동일 결과")
        assertEquals(99.0, collapsedCount("category"), 0.0001, "collapsed = N-1 = 99")

        recordMetric(
            "1-1 cold start collapse",
            mapOf(
                "threads" to threadCount,
                "loader_calls" to loaderCount.get(),
                "collapsed_total" to collapsedCount("category"),
                "miss_total" to missCount("category"),
                "results_received" to results.size,
                "elapsed_ms" to elapsedMs
            )
        )
    }

    @Test
    @DisplayName("[TTL] L1 만료 직후 100 스레드 동시 요청 → loader 1회 호출")
    fun ttl_expired_window_single_flight_collapse() {
        val cache = createNonHotCache(shortTtlPolicy)
        val key = 2002L
        val threadCount = 100

        cache.put(key, "stale-prewarm")
        Thread.sleep(250)

        val loaderCount = AtomicInteger(0)
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val gate = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Any?>()
        val expected = "loaded-after-ttl"

        repeat(threadCount) {
            executor.submit {
                try {
                    barrier.await()
                    val v = cache.get(key, Callable<String> {
                        loaderCount.incrementAndGet()
                        gate.await()
                        expected
                    })
                    results.add(v)
                } finally {
                    latch.countDown()
                }
            }
        }

        Thread.sleep(150)
        gate.countDown()
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(1, loaderCount.get(), "TTL 만료 직후에도 loader는 1회")
        assertEquals(setOf<Any?>(expected), results.toSet())
        assertEquals(99.0, collapsedCount("category-short"), 0.0001)

        recordMetric(
            "TTL expired stampede window",
            mapOf(
                "threads" to threadCount,
                "l1_ttl_ms" to shortTtlPolicy.l1Ttl.toMillis(),
                "loader_calls" to loaderCount.get(),
                "collapsed_total" to collapsedCount("category-short")
            )
        )
    }

    @Test
    @DisplayName("[1-2] loader 예외 발생 시 모든 스레드 동일 예외 + in-flight 정리")
    fun loader_exception_propagates_and_inflight_cleared() {
        val cache = createNonHotCache()
        val key = 3003L
        val threadCount = 100
        val loaderCount = AtomicInteger(0)
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val gate = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<Throwable>()

        repeat(threadCount) {
            executor.submit {
                try {
                    barrier.await()
                    try {
                        cache.get(key, Callable<String> {
                            loaderCount.incrementAndGet()
                            gate.await()
                            throw IllegalStateException("loader-failed-3003")
                        })
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        Thread.sleep(150)
        gate.countDown()
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(1, loaderCount.get(), "예외 발생도 loader 1회로 collapse")
        assertEquals(threadCount, errors.size, "모든 스레드 예외 수신")
        errors.forEach { e ->
            assertTrue(e is IllegalStateException) { "got ${e::class.qualifiedName}" }
            assertEquals("loader-failed-3003", e.message)
        }

        val recoveryLoaderCount = AtomicInteger(0)
        val recoveryValue = cache.get(key, Callable<String> {
            recoveryLoaderCount.incrementAndGet()
            "recovered"
        })
        assertEquals("recovered", recoveryValue, "in-flight 정리 후 다음 요청은 정상 동작")
        assertEquals(1, recoveryLoaderCount.get(), "후속 단일 요청은 loader 정상 호출")

        recordMetric(
            "1-2 loader exception propagation",
            mapOf(
                "threads" to threadCount,
                "loader_calls" to loaderCount.get(),
                "errors_received" to errors.size,
                "post_recovery_loader_calls" to recoveryLoaderCount.get()
            )
        )
    }

    @Test
    @DisplayName("[1-3] 키 A/B 각 50 스레드 동시 요청 → 각 키 loader 1회씩 (서로 비간섭)")
    fun per_key_independence() {
        val cache = createNonHotCache()
        val keyA = 4040L
        val keyB = 5050L
        val threadsPerKey = 50
        val total = threadsPerKey * 2

        val loaderA = AtomicInteger(0)
        val loaderB = AtomicInteger(0)
        val barrier = CyclicBarrier(total)
        val latch = CountDownLatch(total)
        val executor = Executors.newFixedThreadPool(total)
        val gate = CountDownLatch(1)
        val resultsA = ConcurrentLinkedQueue<Any?>()
        val resultsB = ConcurrentLinkedQueue<Any?>()

        repeat(threadsPerKey) {
            executor.submit {
                try {
                    barrier.await()
                    val v = cache.get(keyA, Callable<String> {
                        loaderA.incrementAndGet()
                        gate.await()
                        "A-value"
                    })
                    resultsA.add(v)
                } finally {
                    latch.countDown()
                }
            }
            executor.submit {
                try {
                    barrier.await()
                    val v = cache.get(keyB, Callable<String> {
                        loaderB.incrementAndGet()
                        gate.await()
                        "B-value"
                    })
                    resultsB.add(v)
                } finally {
                    latch.countDown()
                }
            }
        }

        Thread.sleep(200)
        gate.countDown()
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(1, loaderA.get(), "키 A loader 1회")
        assertEquals(1, loaderB.get(), "키 B loader 1회")
        assertEquals(setOf<Any?>("A-value"), resultsA.toSet())
        assertEquals(setOf<Any?>("B-value"), resultsB.toSet())
        assertEquals(threadsPerKey, resultsA.size)
        assertEquals(threadsPerKey, resultsB.size)
        assertEquals(
            (threadsPerKey - 1).toDouble() * 2,
            collapsedCount("category"),
            0.0001,
            "collapsed = (50-1) * 2 = 98"
        )

        recordMetric(
            "1-3 per-key independence",
            mapOf(
                "key_A_threads" to threadsPerKey,
                "key_A_loader_calls" to loaderA.get(),
                "key_B_threads" to threadsPerKey,
                "key_B_loader_calls" to loaderB.get(),
                "collapsed_total" to collapsedCount("category")
            )
        )
    }

    @Test
    @DisplayName("[1-4] hot 키는 분산락 경로, 비-hot 키는 single-flight 경로 (격리)")
    fun hot_and_non_hot_paths_isolated() {
        val detector = FakeHotKeyDetector()
        detector.overrideIsHot = true
        val shardedCache: Cache = mock()
        whenever(shardedCache.get(org.mockito.kotlin.any())).thenReturn(null)

        val hotCaffeine = Caffeine.newBuilder()
            .maximumSize(hotKeyPolicy.l1MaxSize)
            .expireAfterWrite(hotKeyPolicy.l1Ttl)
            .build<Any, Any>()
        val hotCache = TwoLevelCache(
            hotKeyPolicy.cacheName, hotCaffeine, redisCache, hotKeyPolicy,
            lockProvider, shardedCache, detector, meterRegistry
        )

        val nonHotCache = createNonHotCache()

        val hotKey = 6006L
        val nonHotKey = 7007L
        val threadsPerKey = 50
        val total = threadsPerKey * 2

        val hotLoader = AtomicInteger(0)
        val nonHotLoader = AtomicInteger(0)
        val barrier = CyclicBarrier(total)
        val latch = CountDownLatch(total)
        val executor = Executors.newFixedThreadPool(total)
        val gate = CountDownLatch(1)

        repeat(threadsPerKey) {
            executor.submit {
                try {
                    barrier.await()
                    hotCache.get(hotKey, Callable<String> {
                        hotLoader.incrementAndGet()
                        gate.await()
                        "hot-value"
                    })
                } finally {
                    latch.countDown()
                }
            }
            executor.submit {
                try {
                    barrier.await()
                    nonHotCache.get(nonHotKey, Callable<String> {
                        nonHotLoader.incrementAndGet()
                        gate.await()
                        "non-hot-value"
                    })
                } finally {
                    latch.countDown()
                }
            }
        }

        Thread.sleep(200)
        gate.countDown()
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertTrue(lockProvider.lockCallCount >= 1, "hot 키 경로는 분산락 사용")
        assertTrue(lockProvider.unlockCallCount >= 1, "락 획득자는 unlock 호출")
        assertEquals(
            0.0,
            collapsedCount(hotKeyPolicy.cacheName),
            0.0001,
            "hot 경로는 single-flight collapsed 카운터 미증가"
        )
        assertEquals(
            (threadsPerKey - 1).toDouble(),
            collapsedCount(nonHotPolicy.cacheName),
            0.0001,
            "비-hot 경로는 collapsed = 49"
        )
        assertNotNull(hotLoader.get())
        assertEquals(1, nonHotLoader.get(), "비-hot 키 loader 1회 (single-flight collapse)")

        recordMetric(
            "1-4 hot vs non-hot path isolation",
            mapOf(
                "hot_threads" to threadsPerKey,
                "hot_lock_calls" to lockProvider.lockCallCount,
                "hot_unlock_calls" to lockProvider.unlockCallCount,
                "hot_loader_calls" to hotLoader.get(),
                "hot_collapsed_total" to collapsedCount(hotKeyPolicy.cacheName),
                "non_hot_threads" to threadsPerKey,
                "non_hot_loader_calls" to nonHotLoader.get(),
                "non_hot_collapsed_total" to collapsedCount(nonHotPolicy.cacheName)
            )
        )
    }

    private fun recordMetric(label: String, fields: Map<String, Any?>) {
        val outDir = Paths.get("..", "load-tests", "results", "stampede-validation").toAbsolutePath().normalize()
        Files.createDirectories(outDir)
        val file = outDir.resolve("metrics.tsv")
        PrintWriter(Files.newBufferedWriter(file, java.nio.charset.StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)).use { w ->
            val joined = fields.entries.joinToString("\t") { "${it.key}=${it.value}" }
            w.println("$label\t$joined")
        }
    }
}
