package com.hoppingmall.gateway.config

import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class GatewayRedisClientConfig {

    @Bean
    fun gatewayLettuceCommandTimeoutCustomizer(redisProperties: RedisProperties): LettuceClientConfigurationBuilderCustomizer =
        LettuceClientConfigurationBuilderCustomizer { builder ->
            if (redisProperties.timeout == null) {
                builder.commandTimeout(DEFAULT_COMMAND_TIMEOUT)
            }
        }

    companion object {
        val DEFAULT_COMMAND_TIMEOUT: Duration = Duration.ofSeconds(1)
    }
}
