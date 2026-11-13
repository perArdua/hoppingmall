package com.hoppingmall.common.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

@Configuration
@ConditionalOnProperty(name = ["spring.task.scheduling.enabled"], matchIfMissing = true)
class SchedulingConfig : SchedulingConfigurer {

    @Bean(defaultCandidate = false)
    fun schedulingTaskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 4
        scheduler.setThreadNamePrefix("scheduler-")
        return scheduler
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setScheduler(schedulingTaskScheduler())
    }
}
