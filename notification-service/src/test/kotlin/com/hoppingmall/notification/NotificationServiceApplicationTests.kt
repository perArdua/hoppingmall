package com.hoppingmall.notification

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationServiceApplicationTests.TestInfraConfig::class, com.hoppingmall.notification.support.TestCacheConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.kafka.consumer.group-id=notification-service"
])
class NotificationServiceApplicationTests {

    @TestConfiguration
    class TestInfraConfig {

        @Bean
        @Primary
        fun redisConnectionFactory(): RedisConnectionFactory {
            return Mockito.mock(RedisConnectionFactory::class.java)
        }

        @Bean
        @Primary
        fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
            val template = RedisTemplate<String, String>()
            template.connectionFactory = redisConnectionFactory
            template.keySerializer = StringRedisSerializer()
            template.valueSerializer = StringRedisSerializer()
            return template
        }

        @Bean
        @Primary
        fun redisMessageListenerContainer(): RedisMessageListenerContainer {
            return Mockito.mock(RedisMessageListenerContainer::class.java)
        }
    }

    @Test
    fun contextLoads() {
    }
}
