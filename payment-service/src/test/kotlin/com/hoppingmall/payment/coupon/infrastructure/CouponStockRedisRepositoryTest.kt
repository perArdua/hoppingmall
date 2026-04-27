package com.hoppingmall.payment.coupon.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RBucket
import org.redisson.api.RKeys
import org.redisson.api.RScript
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.redisson.client.codec.Codec

@DisplayName("CouponStockRedisRepository")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponStockRedisRepositoryTest {

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var rScript: RScript

    @Mock
    private lateinit var rBucket: RBucket<String>

    @Mock
    private lateinit var rKeys: RKeys

    @InjectMocks
    private lateinit var repository: CouponStockRedisRepository

    @Test
    fun tryReserve_성공_시_Success를_반환한다() {
        whenever(redissonClient.getScript(any<Codec>())).thenReturn(rScript)
        whenever(rScript.eval<Long>(any(), any<String>(), any(), any<List<Any>>(), any())).thenReturn(1L)

        val result = repository.tryReserve(1L, 10L)

        assertThat(result).isEqualTo(CouponReserveResult.Success)
    }

    @Test
    fun tryReserve_재고_소진_시_Exhausted를_반환한다() {
        whenever(redissonClient.getScript(any<Codec>())).thenReturn(rScript)
        whenever(rScript.eval<Long>(any(), any<String>(), any(), any<List<Any>>(), any())).thenReturn(0L)

        val result = repository.tryReserve(1L, 10L)

        assertThat(result).isEqualTo(CouponReserveResult.Exhausted)
    }

    @Test
    fun tryReserve_미초기화_시_NotInitialized를_반환한다() {
        whenever(redissonClient.getScript(any<Codec>())).thenReturn(rScript)
        whenever(rScript.eval<Long>(any(), any<String>(), any(), any<List<Any>>(), any())).thenReturn(-1L)

        val result = repository.tryReserve(1L, 10L)

        assertThat(result).isEqualTo(CouponReserveResult.NotInitialized)
    }

    @Test
    fun tryReserve_이미_발급_시_AlreadyIssued를_반환한다() {
        whenever(redissonClient.getScript(any<Codec>())).thenReturn(rScript)
        whenever(rScript.eval<Long>(any(), any<String>(), any(), any<List<Any>>(), any())).thenReturn(-2L)

        val result = repository.tryReserve(1L, 10L)

        assertThat(result).isEqualTo(CouponReserveResult.AlreadyIssued)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun initializeStock_SETNX로_재고를_초기화한다() {
        whenever(redissonClient.getBucket<String>(eq("coupon:{1}:stock"), any<Codec>())).thenReturn(rBucket as RBucket<String>)
        whenever(rBucket.setIfAbsent("100")).thenReturn(true)

        val result = repository.initializeStock(1L, 100)

        assertThat(result).isTrue()
        verify(rBucket).setIfAbsent("100")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun initializeStock_이미_존재하면_false를_반환한다() {
        whenever(redissonClient.getBucket<String>(eq("coupon:{1}:stock"), any<Codec>())).thenReturn(rBucket as RBucket<String>)
        whenever(rBucket.setIfAbsent("100")).thenReturn(false)

        val result = repository.initializeStock(1L, 100)

        assertThat(result).isFalse()
    }

    @Test
    fun restoreStock_Lua_script로_재고_증가_및_사용자_제거를_수행한다() {
        whenever(redissonClient.getScript(any<Codec>())).thenReturn(rScript)
        whenever(rScript.eval<Long>(any(), any<String>(), any(), any<List<Any>>(), any())).thenReturn(1L)

        repository.restoreStock(1L, 10L)

        verify(rScript).eval<Long>(
            eq(RScript.Mode.READ_WRITE),
            eq(CouponStockRedisRepository.RESTORE_SCRIPT),
            eq(RScript.ReturnType.LONG),
            eq(listOf("coupon:{1}:stock", "coupon:{1}:issued")),
            eq("10")
        )
    }

    @Test
    fun deleteStock_두_키를_모두_삭제한다() {
        whenever(redissonClient.keys).thenReturn(rKeys)

        repository.deleteStock(1L)

        verify(rKeys).delete("coupon:{1}:stock", "coupon:{1}:issued")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun getIssuedUserIds_set_멤버를_Long으로_파싱해서_반환한다() {
        val rSet: RSet<String> = org.mockito.kotlin.mock()
        whenever(redissonClient.getSet<String>(eq("coupon:{1}:issued"), any<Codec>())).thenReturn(rSet)
        whenever(rSet.readAll()).thenReturn(setOf("10", "20", "abc"))

        val result = repository.getIssuedUserIds(1L)

        assertThat(result).containsExactlyInAnyOrder(10L, 20L)
    }
}
