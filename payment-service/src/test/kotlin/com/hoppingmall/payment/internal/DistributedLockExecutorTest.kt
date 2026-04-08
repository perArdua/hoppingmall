package com.hoppingmall.payment.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Duration
import java.util.concurrent.TimeUnit

@DisplayName("DistributedLockExecutor")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class DistributedLockExecutorTest {

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @Mock
    private lateinit var rLock: RLock

    private lateinit var lockExecutor: DistributedLockExecutor

    @BeforeEach
    fun setUp() {
        lockExecutor = DistributedLockExecutor(redissonClient, transactionManager)
    }

    private fun stubTransaction() {
        whenever(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
    }

    @Test
    fun 락_획득_성공_시_트랜잭션_내에서_액션을_실행한다() {
        stubTransaction()
        whenever(redissonClient.getLock("test-key")).thenReturn(rLock)
        whenever(rLock.tryLock(3000L, -1, TimeUnit.MILLISECONDS)).thenReturn(true)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        val result = lockExecutor.withLock("test-key") { "success" }

        assertThat(result).isEqualTo("success")
        verify(transactionManager).commit(any())
        verify(rLock).unlock()
    }

    @Test
    fun 락_획득_실패_시_예외를_던진다() {
        whenever(redissonClient.getLock("test-key")).thenReturn(rLock)
        whenever(rLock.tryLock(3000L, -1, TimeUnit.MILLISECONDS)).thenReturn(false)

        assertThatThrownBy { lockExecutor.withLock("test-key") { "should-not-run" } }
            .isInstanceOf(DistributedLockException::class.java)
    }

    @Test
    fun 액션_예외_발생_시_롤백_후_락을_해제한다() {
        stubTransaction()
        whenever(redissonClient.getLock("test-key")).thenReturn(rLock)
        whenever(rLock.tryLock(3000L, -1, TimeUnit.MILLISECONDS)).thenReturn(true)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        assertThatThrownBy {
            lockExecutor.withLock<String>("test-key") { throw RuntimeException("action failed") }
        }.isInstanceOf(RuntimeException::class.java)

        verify(transactionManager).rollback(any())
        verify(rLock).unlock()
    }

    @Test
    fun withLockVoid는_반환값_없이_실행한다() {
        stubTransaction()
        whenever(redissonClient.getLock("void-key")).thenReturn(rLock)
        whenever(rLock.tryLock(3000L, -1, TimeUnit.MILLISECONDS)).thenReturn(true)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        var executed = false

        lockExecutor.withLockVoid("void-key") { executed = true }

        assertThat(executed).isTrue()
        verify(transactionManager).commit(any())
        verify(rLock).unlock()
    }

    @Test
    fun 커스텀_대기_시간을_사용한다() {
        stubTransaction()
        val waitTime = Duration.ofSeconds(5)
        whenever(redissonClient.getLock("custom-key")).thenReturn(rLock)
        whenever(rLock.tryLock(5000L, -1, TimeUnit.MILLISECONDS)).thenReturn(true)
        whenever(rLock.isHeldByCurrentThread).thenReturn(true)

        val result = lockExecutor.withLock("custom-key", waitTime) { 42 }

        assertThat(result).isEqualTo(42)
        verify(rLock).tryLock(5000L, -1, TimeUnit.MILLISECONDS)
    }
}
