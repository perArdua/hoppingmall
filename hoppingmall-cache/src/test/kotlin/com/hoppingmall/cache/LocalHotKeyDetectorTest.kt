package com.hoppingmall.cache

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@DisplayName("LocalHotKeyDetector")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LocalHotKeyDetectorTest {

    private lateinit var scheduler: ScheduledExecutorService

    @BeforeEach
    fun setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "hotkey-detector-test").apply { isDaemon = true }
        }
    }

    @AfterEach
    fun tearDown() {
        scheduler.shutdownNow()
        scheduler.awaitTermination(2, TimeUnit.SECONDS)
    }

    @Test
    fun 임계값_미만_접근은_핫키로_판별하지_않는다() {
        val detector = LocalHotKeyDetector(threshold = 5, windowDuration = Duration.ofMinutes(10), scheduler = scheduler)

        repeat(4) { detector.recordAccess("key1") }

        assertFalse(detector.isHot("key1"))
        detector.close()
    }

    @Test
    fun 임계값_이상_접근은_핫키로_승격한다() {
        val detector = LocalHotKeyDetector(threshold = 5, windowDuration = Duration.ofMinutes(10), scheduler = scheduler)

        repeat(5) { detector.recordAccess("key1") }

        assertTrue(detector.isHot("key1"))
        detector.close()
    }

    @Test
    fun 키별로_독립적으로_추적한다() {
        val detector = LocalHotKeyDetector(threshold = 3, windowDuration = Duration.ofMinutes(10), scheduler = scheduler)

        repeat(3) { detector.recordAccess("hot-key") }
        repeat(2) { detector.recordAccess("cold-key") }

        assertTrue(detector.isHot("hot-key"))
        assertFalse(detector.isHot("cold-key"))
        detector.close()
    }

    @Test
    fun 동시_접근_시_정확하게_카운팅한다() {
        val detector = LocalHotKeyDetector(threshold = 100, windowDuration = Duration.ofMinutes(10), scheduler = scheduler)
        val threadCount = 10
        val accessPerThread = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            executor.submit {
                try {
                    repeat(accessPerThread) { detector.recordAccess("concurrent-key") }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertTrue(detector.isHot("concurrent-key"))
        detector.close()
    }

    @Test
    fun 윈도우_리셋_후_핫키가_강등된다() {
        val detector = LocalHotKeyDetector(threshold = 3, windowDuration = Duration.ofMillis(100), scheduler = scheduler)

        repeat(3) { detector.recordAccess("key1") }
        assertTrue(detector.isHot("key1"))

        Thread.sleep(300)

        assertFalse(detector.isHot("key1"))
        detector.close()
    }

    @Test
    fun 존재하지_않는_키는_핫키가_아니다() {
        val detector = LocalHotKeyDetector(threshold = 5, windowDuration = Duration.ofMinutes(10), scheduler = scheduler)

        assertFalse(detector.isHot("nonexistent"))
        detector.close()
    }
}
