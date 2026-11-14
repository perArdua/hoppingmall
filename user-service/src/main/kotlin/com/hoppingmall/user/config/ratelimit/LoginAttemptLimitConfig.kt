package com.hoppingmall.user.config.ratelimit

import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock

@Configuration
@EnableConfigurationProperties(LoginAttemptLimitProperties::class)
class LoginAttemptLimitConfig {

    @Bean
    @Profile("!test & !loadtest")
    fun loginAttemptLimiter(redissonClient: RedissonClient, properties: LoginAttemptLimitProperties): LoginAttemptLimiter {
        return RedisLoginAttemptLimiter(redissonClient, Clock.systemUTC(), properties)
    }

    @Bean
    @Profile("test | loadtest")
    fun noOpLoginAttemptLimiter(): LoginAttemptLimiter {
        return NoOpLoginAttemptLimiter()
    }

    @Bean
    fun clientIpResolver(@Value("\${user.rate-limit.trusted-proxy-count:0}") trustedProxyCount: Int): ClientIpResolver {
        return ClientIpResolver(trustedProxyCount)
    }
}
