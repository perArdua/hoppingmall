package com.hoppingmall.product.inventory.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("재고 캐시 비동기 삭제")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InventoryCacheAsyncEvictorTest {

    private val cache = mock<Cache>()
    private val cacheManager = mock<CacheManager>()
    private val cacheManagerProvider = mock<ObjectProvider<CacheManager>>()
    private val meterRegistry = SimpleMeterRegistry()
    private val evictors = mutableListOf<InventoryCacheAsyncEvictor>()

    @BeforeEach
    fun setUp() {
        whenever(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager)
        whenever(cacheManager.getCache("inventory")).thenReturn(cache)
    }

    @AfterEach
    fun tearDown() {
        evictors.forEach { it.shutdown() }
    }

    private fun evictor(capacity: Int = 16) =
        InventoryCacheAsyncEvictor(cacheManagerProvider, meterRegistry, capacity).also { evictors += it }

    private fun count(result: String): Double =
        meterRegistry.counter("inventory.cache.evict.async", "result", result).count()

    private fun blockFirstEvict(): Pair<CountDownLatch, CountDownLatch> {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            if (it.arguments[0] == 1L) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            null
        }.whenever(cache).evict(any())
        return started to release
    }

    @Test
    fun 넘겨받은_상품의_재고_캐시를_실행기에서_지운다() {
        evictor().submit(listOf(4L, 5L))

        verify(cache, timeout(2000)).evict(4L)
        verify(cache, timeout(2000)).evict(5L)
    }

    @Test
    fun 대기_중인_같은_상품은_한_번만_지운다() {
        val (started, release) = blockFirstEvict()
        val evictor = evictor()
        evictor.submit(listOf(1L))
        started.await(2, TimeUnit.SECONDS)

        evictor.submit(listOf(2L))
        evictor.submit(listOf(2L))
        release.countDown()

        verify(cache, timeout(2000)).evict(2L)
        Thread.sleep(100)
        verify(cache, times(1)).evict(2L)
        assertThat(count("merged")).isEqualTo(1.0)
    }

    @Test
    fun 대기열이_차면_버리고_버림_카운터를_올린다() {
        val (started, release) = blockFirstEvict()
        val evictor = evictor(capacity = 1)
        evictor.submit(listOf(1L))
        started.await(2, TimeUnit.SECONDS)

        evictor.submit(listOf(2L, 3L))

        assertThat(evictor.queueSize()).isEqualTo(1)
        assertThat(count("dropped")).isEqualTo(1.0)
        release.countDown()
        verify(cache, timeout(2000)).evict(2L)
        verify(cache, timeout(2000).times(0)).evict(3L)
    }

    @Test
    fun 삭제가_실패해도_호출자에게_번지지_않고_실패_카운터를_올린다() {
        doThrow(IllegalStateException("redis down")).whenever(cache).evict(7L)

        evictor().submit(listOf(7L))

        verify(cache, timeout(2000)).evict(7L)
        Thread.sleep(100)
        assertThat(count("failed")).isEqualTo(1.0)
        assertThat(count("evicted")).isEqualTo(0.0)
    }
}
