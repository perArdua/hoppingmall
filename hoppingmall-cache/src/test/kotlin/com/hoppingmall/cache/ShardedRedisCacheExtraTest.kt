package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import java.util.concurrent.Callable

@DisplayName("ShardedRedisCache 추가 경로")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ShardedRedisCacheExtraTest {

    private val delegate: Cache = mock()
    private val nativeCacheObj = Any()
    private val shardedCache = ShardedRedisCache(delegate, 4)

    @Test
    fun getNativeCache는_delegate의_nativeCache를_반환한다() {
        whenever(delegate.nativeCache).thenReturn(nativeCacheObj)

        assertThat(shardedCache.nativeCache).isSameAs(nativeCacheObj)
    }

    @Test
    fun get_타입_지정_샤드_히트_시_값을_반환한다() {
        whenever(delegate.get(any<String>(), eq(String::class.java))).thenReturn("shard-typed-value")

        val result = shardedCache.get("key1", String::class.java)

        assertThat(result).isEqualTo("shard-typed-value")
    }

    @Test
    fun get_타입_지정_샤드_미스_원본_히트_시_값을_반환한다() {
        whenever(delegate.get(any<String>(), eq(String::class.java))).thenAnswer { inv ->
            val key = inv.getArgument<String>(0)
            if (key.contains("::shard:")) null else "original-typed-value"
        }

        val result = shardedCache.get("key1", String::class.java)

        assertThat(result).isEqualTo("original-typed-value")
    }

    @Test
    fun get_타입_지정_모두_미스이면_null을_반환한다() {
        whenever(delegate.get(any<String>(), eq(String::class.java))).thenReturn(null)

        val result = shardedCache.get("key1", String::class.java)

        assertThat(result).isNull()
    }

    @Test
    fun get_valueLoader는_delegate에_위임한다() {
        val loader = Callable { "loader-result" }
        whenever(delegate.get("key1", loader)).thenReturn("loader-result")

        val result = shardedCache.get("key1", loader)

        assertThat(result).isEqualTo("loader-result")
    }
}
