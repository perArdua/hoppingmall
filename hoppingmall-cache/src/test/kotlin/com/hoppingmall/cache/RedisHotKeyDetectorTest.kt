package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.redisson.api.RBatch
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RScoredSortedSetAsync
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@DisplayName("RedisHotKeyDetector")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RedisHotKeyDetectorTest {

    private lateinit var scheduler: ScheduledExecutorService

    @BeforeEach
    fun setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "redis-hotkey-test").apply { isDaemon = true }
        }
    }

    @AfterEach
    fun tearDown() {
        scheduler.shutdownNow()
        scheduler.awaitTermination(2, TimeUnit.SECONDS)
    }

    private fun newDetector(
        threshold: Long = 50,
        windowMs: Long = 60_000L,
        flushIntervalMs: Long = Duration.ofHours(1).toMillis(),
        redissonClient: RedissonClient
    ): RedisHotKeyDetector {
        return RedisHotKeyDetector(
            cacheName = "product",
            threshold = threshold,
            windowMs = windowMs,
            redissonClient = redissonClient,
            scheduler = scheduler,
            flushIntervalMs = flushIntervalMs
        )
    }

    @Test
    fun recordAccess_호출_시_동기_Redis_호출이_발생하지_않는다() {
        val redisson: RedissonClient = mock()
        val detector = newDetector(redissonClient = redisson)

        repeat(1000) { detector.recordAccess("key1") }

        verifyNoInteractions(redisson)
        detector.close()
    }

    @Test
    fun 단일_인스턴스에서_임계값_이상_접근_후_플러시_사이클_후_핫키를_판별한다() {
        val redisson: RedissonClient = mock()
        val batch: RBatch = mock()
        val asyncSet: RScoredSortedSetAsync<String> = mock()
        val readSet: RScoredSortedSet<String> = mock()
        whenever(redisson.createBatch()).thenReturn(batch)
        whenever(batch.getScoredSortedSet<String>(any<String>())).thenReturn(asyncSet)
        whenever(redisson.getScoredSortedSet<String>(any<String>())).thenReturn(readSet)
        whenever(readSet.valueRange(50.0, true, Double.MAX_VALUE, true))
            .thenReturn(setOf("key1"))
            .thenReturn(emptySet())

        val detector = newDetector(threshold = 50, redissonClient = redisson)
        repeat(50) { detector.recordAccess("key1") }
        detector.flush()

        assertThat(detector.isHot("key1")).isTrue()
        detector.close()
    }

    @Test
    fun Redis_장애_시_예외가_전파되지_않고_로컬_스냅샷으로_폴백한다() {
        val redisson: RedissonClient = mock()
        whenever(redisson.createBatch()).thenThrow(RuntimeException("redis down"))
        whenever(redisson.getScoredSortedSet<String>(any<String>())).thenThrow(RuntimeException("redis down"))

        val detector = newDetector(redissonClient = redisson)
        repeat(50) { detector.recordAccess("key1") }
        detector.flush()

        assertThat(detector.isHot("key1")).isFalse()
        detector.close()
    }

    @Test
    fun flush_시_누적된_카운트를_Redis_ZSet에_addScoreAsync로_반영한다() {
        val redisson: RedissonClient = mock()
        val batch: RBatch = mock()
        val asyncSet: RScoredSortedSetAsync<String> = mock()
        val readSet: RScoredSortedSet<String> = mock()
        whenever(redisson.createBatch()).thenReturn(batch)
        whenever(batch.getScoredSortedSet<String>(any<String>())).thenReturn(asyncSet)
        whenever(redisson.getScoredSortedSet<String>(any<String>())).thenReturn(readSet)
        whenever(readSet.valueRange(any(), any(), any(), any())).thenReturn(emptySet())

        val detector = newDetector(threshold = 50, redissonClient = redisson)
        repeat(10) { detector.recordAccess("key1") }
        detector.flush()

        val keyCaptor = argumentCaptor<String>()
        val scoreCaptor = argumentCaptor<Double>()
        verify(asyncSet, atLeastOnce()).addScoreAsync(keyCaptor.capture(), scoreCaptor.capture())
        assertThat(keyCaptor.firstValue).isEqualTo("key1")
        assertThat(scoreCaptor.firstValue).isEqualTo(10.0)
        verify(asyncSet).expireAsync(any<Duration>())
        verify(batch).execute()
        detector.close()
    }

    @Test
    fun flush_시_현재_및_이전_에포크_ZSet의_핫키를_합집합으로_읽는다() {
        val redisson: RedissonClient = mock()
        val readSet: RScoredSortedSet<String> = mock()
        whenever(redisson.getScoredSortedSet<String>(any<String>())).thenReturn(readSet)
        whenever(readSet.valueRange(any(), any(), any(), any()))
            .thenReturn(setOf("currentKey"))
            .thenReturn(setOf("previousKey"))

        val detector = newDetector(threshold = 50, redissonClient = redisson)
        detector.flush()

        assertThat(detector.isHot("currentKey")).isTrue()
        assertThat(detector.isHot("previousKey")).isTrue()
        detector.close()
    }

    @Test
    fun pendingCounts가_비어있으면_Redis_batch를_생성하지_않는다() {
        val redisson: RedissonClient = mock()
        val readSet: RScoredSortedSet<String> = mock()
        whenever(redisson.getScoredSortedSet<String>(any<String>())).thenReturn(readSet)
        whenever(readSet.valueRange(any(), any(), any(), any())).thenReturn(emptySet())

        val detector = newDetector(redissonClient = redisson)
        detector.flush()

        verify(redisson, never()).createBatch()
        detector.close()
    }

    @Test
    fun 두_인스턴스_시뮬레이션에서_각_25회_recordAccess_후_플러시_시_동일_핫키를_판별한다() {
        val redisson: RedissonClient = mock()
        val batch: RBatch = mock()
        val asyncSet: RScoredSortedSetAsync<String> = mock()
        val readSet: RScoredSortedSet<String> = mock()
        whenever(redisson.createBatch()).thenReturn(batch)
        whenever(batch.getScoredSortedSet<String>(any<String>())).thenReturn(asyncSet)
        whenever(redisson.getScoredSortedSet<String>(any<String>())).thenReturn(readSet)
        whenever(readSet.valueRange(50.0, true, Double.MAX_VALUE, true)).thenReturn(setOf("hotKey"))

        val instanceA = newDetector(threshold = 50, redissonClient = redisson)
        val instanceB = newDetector(threshold = 50, redissonClient = redisson)
        repeat(25) { instanceA.recordAccess("hotKey") }
        repeat(25) { instanceB.recordAccess("hotKey") }
        instanceA.flush()
        instanceB.flush()

        assertThat(instanceA.isHot("hotKey")).isTrue()
        assertThat(instanceB.isHot("hotKey")).isTrue()
        instanceA.close()
        instanceB.close()
    }

    @Test
    fun close는_no_op이며_스케줄러는_레지스트리가_관리한다() {
        val redisson: RedissonClient = mock()
        val detector = newDetector(redissonClient = redisson)

        detector.close()

        assertThat(scheduler.isShutdown).isFalse()
    }
}
