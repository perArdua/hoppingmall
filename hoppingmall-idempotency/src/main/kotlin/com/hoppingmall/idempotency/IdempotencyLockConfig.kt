package com.hoppingmall.idempotency

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(RedissonAutoConfigurationCustomizer::class)
@EnableConfigurationProperties(IdempotencyLockProperties::class)
class IdempotencyLockConfig {

    @Bean
    fun idempotencyLockWatchdogCustomizer(properties: IdempotencyLockProperties): RedissonAutoConfigurationCustomizer =
        RedissonAutoConfigurationCustomizer { config ->
            config.lockWatchdogTimeout = properties.watchdogTimeout.toMillis()
        }
}
