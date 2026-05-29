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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@DisplayName("TwoLevelCache single-flight")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheSingleFlightTest {

    private val redisCache: Cache = mock()
    private lateinit var meterRegistry: SimpleMeterRegistry

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
        whenever(redisCache.get(org.mockito.kotlin.any())).thenReturn(null)
    }

    private fun createNonHotCache(): TwoLevelCache {
        val caffeineCache = Caffeine.newBuilder()
            .maximumSize(normalPolicy.l1MaxSize)
            .expireAfterWrite(normalPolicy.l1Ttl)
            .build<Any, Any>()
        return TwoLevelCache(
            name = normalPolicy.cacheName,
            caffeineCache = caffeineCache,
            redisCache = redisCache,
            policy = normalPolicy,
            meterRegistry = meterRegistry
        )
    }

    private fun collapsedCount(cacheName: String = "category"): Double =
        meterRegistry.counter("cache.singleflight.collapsed", "cache", cacheName).count()

    @Nested
    @DisplayName("하드미스 동시 요청")
    inner class HardMissConcurrency {

        @Test
        fun 동시_100_스레드_cache_miss_시_DB_로더는_1회만_호출된다() {
            val cache = createNonHotCache()
            val threadCount = 100
            val loaderCount = AtomicInteger(0)
            val barrier = CyclicBarrier(threadCount)
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)
            val gate = CountDownLatch(1)

            val results = ConcurrentLinkedQueue<Any?>()

            repeat(threadCount) {
                executor.submit {
                    try {
                        barrier.await()
                        val v = cache.get(1L, Callable<String> {
                            loaderCount.incrementAndGet()
                            gate.await()
                            "loaded-value"
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

            assertEquals(1, loaderCount.get(), "loader 호출은 정확히 1회")
            assertEquals(threadCount, results.size)
        }

        @Test
        fun 모든_스레드가_동일한_결과를_수신한다() {
            val cache = createNonHotCache()
            val threadCount = 100
            val barrier = CyclicBarrier(threadCount)
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)
            val gate = CountDownLatch(1)
            val expected = "single-result"
            val results = ConcurrentLinkedQueue<Any?>()

            repeat(threadCount) {
                executor.submit {
                    try {
                        barrier.await()
                        val v = cache.get(2L, Callable<String> {
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

            assertEquals(threadCount, results.size)
            assertEquals(setOf<Any?>(expected), results.toSet())
        }

        @Test
        fun collapsed_메트릭이_99_증가한다() {
            val cache = createNonHotCache()
            val threadCount = 100
            val barrier = CyclicBarrier(threadCount)
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)
            val gate = CountDownLatch(1)

            repeat(threadCount) {
                executor.submit {
                    try {
                        barrier.await()
                        cache.get(3L, Callable<String> {
                            gate.await()
                            "v"
                        })
                    } finally {
                        latch.countDown()
                    }
                }
            }

            Thread.sleep(150)
            gate.countDown()
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            executor.shutdown()

            assertEquals(99.0, collapsedCount(), 0.0001)
        }
    }

    @Nested
    @DisplayName("예외 전파")
    inner class ExceptionPropagation {

        @Test
        fun DB_로더_예외_시_모든_대기_스레드가_동일_예외를_전파받는다() {
            val cache = createNonHotCache()
            val threadCount = 50
            val barrier = CyclicBarrier(threadCount)
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)
            val gate = CountDownLatch(1)
            val loaderCount = AtomicInteger(0)
            val errors = ConcurrentLinkedQueue<Throwable>()

            repeat(threadCount) {
                executor.submit {
                    try {
                        barrier.await()
                        try {
                            cache.get(4L, Callable<String> {
                                loaderCount.incrementAndGet()
                                gate.await()
                                throw IllegalStateException("loader-failed")
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

            assertEquals(1, loaderCount.get())
            assertEquals(threadCount, errors.size)
            errors.forEach { e ->
                assertTrue(e is IllegalStateException) { "got ${e::class.qualifiedName}" }
                assertEquals("loader-failed", e.message)
            }
        }
    }

    @Nested
    @DisplayName("null 결과")
    inner class NullResult {

        @Test
        fun DB_로더가_null을_반환하면_모든_스레드가_null을_수신한다() {
            val cache = createNonHotCache()
            val threadCount = 30
            val barrier = CyclicBarrier(threadCount)
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)
            val gate = CountDownLatch(1)
            val loaderCount = AtomicInteger(0)
            val nullCount = AtomicInteger(0)

            repeat(threadCount) {
                executor.submit {
                    try {
                        barrier.await()
                        val v = cache.get(5L, Callable<String?> {
                            loaderCount.incrementAndGet()
                            gate.await()
                            null
                        })
                        if (v == null) nullCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            Thread.sleep(150)
            gate.countDown()
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            executor.shutdown()

            assertEquals(1, loaderCount.get())
            assertEquals(threadCount, nullCount.get())
        }
    }

    @Nested
    @DisplayName("fail-open fallback")
    inner class FailOpenFallback {

        @Test
        fun timeout_시_follower는_DB_로더를_직접_호출한다() {
            val cache = createNonHotCache()
            val loaderCount = AtomicInteger(0)
            val creatorReady = CountDownLatch(1)
            val creatorRelease = CountDownLatch(1)
            val followerDone = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val creatorResult = AtomicReference<Any?>()
            val followerResult = AtomicReference<Any?>()

            executor.submit {
                creatorResult.set(cache.get(6L, Callable<String> {
                    loaderCount.incrementAndGet()
                    creatorReady.countDown()
                    creatorRelease.await()
                    "creator-value"
                }))
            }

            assertTrue(creatorReady.await(2, TimeUnit.SECONDS))

            executor.submit {
                try {
                    followerResult.set(cache.get(6L, Callable<String> {
                        loaderCount.incrementAndGet()
                        "follower-fallback-value"
                    }))
                } finally {
                    followerDone.countDown()
                }
            }

            assertTrue(followerDone.await(2, TimeUnit.SECONDS), "follower must time out within 500ms and fallback")

            creatorRelease.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

            assertEquals(2, loaderCount.get(), "creator + follower-fallback = 2 loader calls")
            assertEquals(1.0, collapsedCount(), 0.0001)
            assertNotNull(followerResult.get())
            assertNotNull(creatorResult.get())
        }

        @Test
        fun interrupt_시_follower는_DB_로더를_직접_호출한다() {
            val cache = createNonHotCache()
            val loaderCount = AtomicInteger(0)
            val creatorReady = CountDownLatch(1)
            val creatorRelease = CountDownLatch(1)
            val followerStarted = CountDownLatch(1)
            val followerDone = CountDownLatch(1)
            val followerInterruptedFlag = AtomicBoolean(false)

            val creatorThread = Thread {
                cache.get(7L, Callable<String> {
                    loaderCount.incrementAndGet()
                    creatorReady.countDown()
                    creatorRelease.await()
                    "creator-value"
                })
            }
            creatorThread.start()
            assertTrue(creatorReady.await(2, TimeUnit.SECONDS))

            val followerThread = Thread {
                followerStarted.countDown()
                try {
                    cache.get(7L, Callable<String> {
                        loaderCount.incrementAndGet()
                        "follower-fallback"
                    })
                } finally {
                    followerInterruptedFlag.set(Thread.currentThread().isInterrupted)
                    followerDone.countDown()
                }
            }
            followerThread.start()
            assertTrue(followerStarted.await(2, TimeUnit.SECONDS))

            Thread.sleep(50)
            followerThread.interrupt()

            assertTrue(followerDone.await(2, TimeUnit.SECONDS), "follower must finish after interrupt + fallback")

            creatorRelease.countDown()
            creatorThread.join(5_000)

            assertEquals(2, loaderCount.get(), "creator + follower-fallback after interrupt = 2 loader calls")
            assertTrue(followerInterruptedFlag.get(), "interrupt flag restored on follower thread")
        }
    }
}
