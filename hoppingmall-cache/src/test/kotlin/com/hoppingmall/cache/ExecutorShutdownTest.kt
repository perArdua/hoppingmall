package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@DisplayName("ExecutorShutdown")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ExecutorShutdownTest {

    private fun startUninterruptibleTask(executor: ExecutorService, stop: AtomicBoolean) {
        val started = CountDownLatch(1)
        executor.execute {
            started.countDown()
            while (!stop.get()) {
                Thread.onSpinWait()
            }
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun 대기_중인_작업은_버리고_진행_중인_작업을_끊은_뒤_풀을_닫는다() {
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val queuedRan = AtomicBoolean(false)
        executor.execute {
            started.countDown()
            try {
                CountDownLatch(1).await()
            } catch (e: InterruptedException) {
                interrupted.set(true)
            }
        }
        executor.execute { queuedRan.set(true) }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

        ExecutorShutdown.closeNow(executor, "test")

        assertThat(executor.isTerminated).isTrue()
        assertThat(interrupted.get()).isTrue()
        assertThat(queuedRan.get()).isFalse()
    }

    @Test
    fun 끊기에_응하지_않는_작업이_있으면_정한_시간만_기다리고_돌아온다() {
        val executor = Executors.newSingleThreadExecutor()
        val stop = AtomicBoolean(false)
        startUninterruptibleTask(executor, stop)

        try {
            ExecutorShutdown.closeNow(executor, "test", Duration.ofMillis(50))

            assertThat(executor.isShutdown).isTrue()
            assertThat(executor.isTerminated).isFalse()
        } finally {
            stop.set(true)
        }
    }

    @Test
    fun 기다리는_중에_호출_스레드가_끊기면_끊김_표시를_되살리고_돌아온다() {
        val executor = Executors.newSingleThreadExecutor()
        val stop = AtomicBoolean(false)
        startUninterruptibleTask(executor, stop)

        try {
            Thread.currentThread().interrupt()
            ExecutorShutdown.closeNow(executor, "test")

            assertThat(Thread.interrupted()).isTrue()
            assertThat(executor.isShutdown).isTrue()
        } finally {
            stop.set(true)
        }
    }
}
