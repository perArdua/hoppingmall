package com.hoppingmall.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@DisplayName("공통 스케줄러 빈 등록")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SchedulingConfigBeanTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(TaskExecutionAutoConfiguration::class.java, TaskSchedulingAutoConfiguration::class.java)
        )
        .withUserConfiguration(SchedulingEnabledConfig::class.java, SchedulingConfig::class.java, ThreadRecordingJob::class.java)

    @Test
    fun 스케줄_작업은_공통_스케줄러_빈의_스레드에서_돈다() {
        contextRunner.run { context ->
            assertThat(context).hasBean("schedulingTaskScheduler")
            val job = context.getBean(ThreadRecordingJob::class.java)

            assertThat(job.ran.await(10, TimeUnit.SECONDS)).isTrue()
            assertThat(job.threadName.get()).startsWith("scheduler-")
        }
    }

    @Test
    fun 공통_스케줄러가_있어도_기본_작업_실행기는_그대로_만들어진다() {
        contextRunner.run { context ->
            assertThat(context).hasBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
            assertThat(context.getBean("schedulingTaskScheduler")).isInstanceOf(ThreadPoolTaskScheduler::class.java)
        }
    }

    @Test
    fun 스케줄링을_끄면_공통_스케줄러_빈을_만들지_않는다() {
        contextRunner.withPropertyValues("spring.task.scheduling.enabled=false").run { context ->
            assertThat(context).doesNotHaveBean("schedulingTaskScheduler")
        }
    }

    @Configuration
    @EnableScheduling
    class SchedulingEnabledConfig

    class ThreadRecordingJob {
        val ran = CountDownLatch(1)
        val threadName = AtomicReference<String>()

        @Scheduled(fixedDelay = 60_000)
        fun run() {
            threadName.compareAndSet(null, Thread.currentThread().name)
            ran.countDown()
        }
    }
}
