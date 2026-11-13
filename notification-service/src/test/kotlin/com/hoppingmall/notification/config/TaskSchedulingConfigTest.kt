package com.hoppingmall.notification.config

import com.hoppingmall.notification.support.TestCacheConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@DisplayName("알림 서비스 스케줄 작업 등록")
@DisplayNameGeneration(ReplaceUnderscores::class)
@SpringBootTest
@ActiveProfiles("test")
@Import(TaskSchedulingConfigTest.TestInfraConfig::class, TestCacheConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.kafka.consumer.group-id=notification-service"
])
class TaskSchedulingConfigTest {

    @TestConfiguration
    class TestInfraConfig {

        @Bean
        @Primary
        fun redisConnectionFactory(): RedisConnectionFactory =
            Mockito.mock(RedisConnectionFactory::class.java)

        @Bean
        @Primary
        @Suppress("UNCHECKED_CAST")
        fun redisTemplate(): RedisTemplate<String, String> =
            Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, String>

        @Bean
        @Primary
        fun redisMessageListenerContainer(): RedisMessageListenerContainer =
            Mockito.mock(RedisMessageListenerContainer::class.java)
    }

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
