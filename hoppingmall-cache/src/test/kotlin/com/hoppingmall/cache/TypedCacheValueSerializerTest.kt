package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.cache.support.NullValue
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * TypedCacheValueSerializer 단위 검증 — Redis 불필요(직렬화 ↔ 역직렬화 바이트 왕복만).
 * 임베디드 redis가 skip되는 CI에서도 직렬화기 핵심 로직을 항상 검증한다.
 */
@DisplayName("TypedCacheValueSerializer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TypedCacheValueSerializerTest {

    enum class Status { A, B }
    data class Sample(val id: Long, val price: BigDecimal, val at: LocalDateTime, val tags: List<String>, val status: Status)

    private val single = TypedCacheValueSerializer(
        CacheValueSerializer.mapper,
        CacheValueSerializer.typeOf(Sample::class.java)
    )

    private fun sample(id: Long = 1L) =
        Sample(id, BigDecimal("10.5"), LocalDateTime.of(2026, 1, 1, 0, 0), listOf("x", "y"), Status.A)

    @Test
    fun 단일_값이_선언타입으로_왕복된다() {
        val v = sample()
        assertThat(single.deserialize(single.serialize(v))).isEqualTo(v)
    }

    @Test
    fun 컬렉션_값이_제네릭_타입으로_왕복된다() {
        val listSerializer = TypedCacheValueSerializer(
            CacheValueSerializer.mapper,
            CacheValueSerializer.listOf(Sample::class.java)
        )
        val list = listOf(sample(1L), sample(2L))
        assertThat(listSerializer.deserialize(listSerializer.serialize(list))).isEqualTo(list)
    }

    @Test
    fun null은_빈배열로_직렬화되고_역직렬화시_null이_된다() {
        assertThat(single.serialize(null)).isEmpty()
        assertThat(single.deserialize(ByteArray(0))).isNull()
        assertThat(single.deserialize(null)).isNull()
    }

    @Test
    fun NullValue는_전용마커로_왕복되어_NullValue로_복원된다() {
        val bytes = single.serialize(NullValue.INSTANCE)
        assertThat(single.deserialize(bytes)).isEqualTo(NullValue.INSTANCE)
    }
}
