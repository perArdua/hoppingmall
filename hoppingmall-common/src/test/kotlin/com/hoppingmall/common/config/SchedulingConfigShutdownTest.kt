package com.hoppingmall.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@DisplayName("공통 스케줄러 종료 때 실행 중인 배치 대기")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SchedulingConfigShutdownTest {

    @Test
    fun 컨텍스트를_닫으면_실행_중인_배치가_끝난_뒤에_닫힌다() {
        val context = AnnotationConfigApplicationContext(
            SchedulingEnabledConfig::class.java,
            SchedulingConfig::class.java,
            SlowBatch::class.java
        )
        val batch = context.getBean(SlowBatch::class.java)
        assertThat(batch.started.await(10, TimeUnit.SECONDS)).isTrue()

        context.close()

        assertThat(batch.interrupted.get()).isFalse()
        assertThat(batch.completed.get()).isTrue()
    }

    @Configuration
    @EnableScheduling
    class SchedulingEnabledConfig

    class SlowBatch {
        val started = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        val interrupted = AtomicBoolean(false)

        @Scheduled(fixedDelay = 60_000)
        fun run() {
            started.countDown()
            try {
                Thread.sleep(BATCH_MILLIS)
                completed.set(true)
            } catch (e: InterruptedException) {
                interrupted.set(true)
            }
        }
    }

    companion object {
        private const val BATCH_MILLIS = 1_500L
    }
}
