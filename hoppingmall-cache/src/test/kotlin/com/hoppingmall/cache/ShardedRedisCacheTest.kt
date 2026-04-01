package com.hoppingmall.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache

@DisplayName("ShardedRedisCache")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ShardedRedisCacheTest {

    private val delegate: Cache = mock()
    private val shardCount = 4
    private val shardedCache = ShardedRedisCache(delegate, shardCount)

    @Test
    fun 샤드_또는_원본_키에서_값을_조회한다() {
        val valueWrapper: Cache.ValueWrapper = mock()
        whenever(valueWrapper.get()).thenReturn("cached-value")
        whenever(delegate.get(eq("key1"))).thenReturn(valueWrapper)

        val result = shardedCache.get("key1")

        assertEquals("cached-value", result?.get())
    }

    @Test
    fun 모든_샤드와_원본에_미스이면_null을_반환한다() {
        whenever(delegate.get(any())).thenReturn(null)

        val result = shardedCache.get("missing-key")

        assertNull(result)
    }

    @Test
    fun put은_원본과_전체_샤드에_저장한다() {
        shardedCache.put("key1", "value1")

        verify(delegate).put("key1", "value1")
        repeat(shardCount) { i ->
            verify(delegate).put("key1::shard:$i", "value1")
        }
    }

    @Test
    fun evict은_원본과_전체_샤드를_삭제한다() {
        shardedCache.evict("key1")

        verify(delegate).evict("key1")
        repeat(shardCount) { i ->
            verify(delegate).evict("key1::shard:$i")
        }
    }

    @Test
    fun clear는_delegate에_위임한다() {
        shardedCache.clear()

        verify(delegate).clear()
    }

    @Test
    fun name은_delegate의_name을_반환한다() {
        whenever(delegate.name).thenReturn("product")

        assertEquals("product", shardedCache.name)
    }
}
