package com.hoppingmall.user

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@Import(UserServiceApplicationTests.TestInfraConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test"
])
class UserServiceApplicationTests {

    @TestConfiguration
    class TestInfraConfig {

        @Bean
        @Primary
        fun redisConnectionFactory(): RedisConnectionFactory {
            return Mockito.mock(RedisConnectionFactory::class.java)
        }

        @Bean
        @Primary
        fun stringRedisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
            val template = RedisTemplate<String, String>()
            template.connectionFactory = redisConnectionFactory
            template.keySerializer = StringRedisSerializer()
            template.valueSerializer = StringRedisSerializer()
            return template
        }

    }

    @Test
    fun contextLoads() {
    }
}
