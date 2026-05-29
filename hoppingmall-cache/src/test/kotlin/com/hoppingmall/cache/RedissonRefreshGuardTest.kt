package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@DisplayName("RedissonRefreshGuard")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RedissonRefreshGuardTest {

    private val redissonClient: RedissonClient = mock()
    private val bucket: RBucket<String> = mock()
    private lateinit var guard: RedissonRefreshGuard

    @BeforeEach
    fun setUp() {
        whenever(redissonClient.getBucket<String>(any<String>())).thenReturn(bucket)
        guard = RedissonRefreshGuard(redissonClient, instanceId = "instance-1")
    }

    @Test
    fun tryAcquire_성공_시_true를_반환한다() {
        whenever(bucket.trySet(eq("instance-1"), eq(3000L), eq(TimeUnit.MILLISECONDS))).thenReturn(true)

        val result = guard.tryAcquire("refresh:product:1", Duration.ofSeconds(3))

        assertThat(result).isTrue()
        verify(redissonClient).getBucket<String>("refresh:product:1")
    }

    @Test
    fun tryAcquire_경합_시_false를_반환한다() {
        whenever(bucket.trySet(eq("instance-1"), eq(3000L), eq(TimeUnit.MILLISECONDS))).thenReturn(false)

        val result = guard.tryAcquire("refresh:product:1", Duration.ofSeconds(3))

        assertThat(result).isFalse()
    }

    @Test
    fun markFailed_는_짧은_쿨다운으로_set한다() {
        guard.markFailed("refresh:product:1", Duration.ofMillis(200))

        verify(bucket).set("instance-1", 200L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun instanceId_미지정_시_랜덤_UUID가_생성된다() {
        val g = RedissonRefreshGuard(redissonClient)
        assertThat(g.instanceId).isNotBlank()
    }
}
