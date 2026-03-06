package com.hoppingmall.mall.global.common.lock

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@DisplayName("DistributedLockExecutor")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class DistributedLockExecutorTest {

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var rLock: RLock

    @InjectMocks
    private lateinit var lockExecutor: DistributedLockExecutor

    @Test
    fun 락_획득_성공_시_액션을_실행한다() {
        // Context
        whenever(redissonClient.getLock("test-key")).thenReturn(rLock)
        whenever(rLock.tryLock(3000L, 5000L, TimeUnit.MILLISECONDS)).thenReturn(true)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        // Interaction
        val result = lockExecutor.withLock("test-key") { "success" }

        // Assertions
        assertThat(result).isEqualTo("success")
        verify(rLock).unlock()
    }

    @Test
    fun 락_획득_실패_시_예외를_던진다() {
        // Context
        whenever(redissonClient.getLock("test-key")).thenReturn(rLock)
        whenever(rLock.tryLock(3000L, 5000L, TimeUnit.MILLISECONDS)).thenReturn(false)

        // Interaction & Assertions
        assertThatThrownBy { lockExecutor.withLock("test-key") { "should-not-run" } }
            .isInstanceOf(DistributedLockException::class.java)
    }

    @Test
    fun 액션_예외_발생_시에도_락을_해제한다() {
        // Context
        whenever(redissonClient.getLock("test-key")).thenReturn(rLock)
        whenever(rLock.tryLock(3000L, 5000L, TimeUnit.MILLISECONDS)).thenReturn(true)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        // Interaction & Assertions
        assertThatThrownBy {
            lockExecutor.withLock("test-key") { throw RuntimeException("action failed") }
        }.isInstanceOf(RuntimeException::class.java)

        verify(rLock).unlock()
    }

    @Test
    fun 커스텀_대기_시간과_임대_시간을_사용한다() {
        // Context
        val waitTime = Duration.ofSeconds(5)
        val leaseTime = Duration.ofSeconds(10)
        whenever(redissonClient.getLock("custom-key")).thenReturn(rLock)
        whenever(rLock.tryLock(5000L, 10000L, TimeUnit.MILLISECONDS)).thenReturn(true)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        // Interaction
        val result = lockExecutor.withLock("custom-key", waitTime, leaseTime) { 42 }

        // Assertions
        assertThat(result).isEqualTo(42)
        verify(rLock).tryLock(5000L, 10000L, TimeUnit.MILLISECONDS)
    }
}
