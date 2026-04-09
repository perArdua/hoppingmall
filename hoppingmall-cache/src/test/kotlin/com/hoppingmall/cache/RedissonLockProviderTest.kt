package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@DisplayName("RedissonLockProvider")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RedissonLockProviderTest {

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var rLock: RLock

    @InjectMocks
    private lateinit var lockProvider: RedissonLockProvider

    @Test
    fun tryLock_성공_시_true를_반환한다() {
        whenever(redissonClient.getLock("lock:key")).thenReturn(rLock)
        whenever(rLock.tryLock(0L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(true)

        val result = lockProvider.tryLock("lock:key", Duration.ofSeconds(3))

        assertThat(result).isTrue()
    }

    @Test
    fun tryLock_실패_시_false를_반환한다() {
        whenever(redissonClient.getLock("lock:key")).thenReturn(rLock)
        whenever(rLock.tryLock(0L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(false)

        val result = lockProvider.tryLock("lock:key", Duration.ofSeconds(3))

        assertThat(result).isFalse()
    }

    @Test
    fun unlock_현재_스레드가_락_보유_시_해제한다() {
        whenever(redissonClient.getLock("lock:key")).thenReturn(rLock)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        lockProvider.unlock("lock:key")

        verify(rLock).unlock()
    }

    @Test
    fun unlock_현재_스레드가_락_미보유_시_unlock을_호출하지_않는다() {
        whenever(redissonClient.getLock("lock:key")).thenReturn(rLock)
        whenever(rLock.isHeldByCurrentThread).thenReturn(false)

        lockProvider.unlock("lock:key")

        verify(rLock, never()).unlock()
    }
}
