package com.hoppingmall.user.config

import com.hoppingmall.user.UserServiceApplicationTests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.context.TestPropertySource

@DisplayName("사용자 서비스 스케줄 작업 등록")
@DisplayNameGeneration(ReplaceUnderscores::class)
@SpringBootTest
@Import(UserServiceApplicationTests.TestInfraConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test"
])
class TaskSchedulingConfigTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun DLQ_자동_재처리_작업이_스케줄에_등록된다() {
        assertThat(scheduledTaskNames())
            .contains("com.hoppingmall.dlq.service.DLQScheduler.autoRetryPendingMessages")
    }

    private fun scheduledTaskNames(): List<String> =
        applicationContext.getBeansOfType(ScheduledTaskHolder::class.java).values
            .flatMap { it.scheduledTasks }
            .map { it.task.toString() }
}
