package com.hoppingmall.user.config.ratelimit

import org.redisson.api.RedissonClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock

@Configuration
class LoginAttemptLimitConfig {

    @Bean
    @Profile("!test & !loadtest")
    fun loginAttemptLimiter(redissonClient: RedissonClient): LoginAttemptLimiter {
        return RedisLoginAttemptLimiter(redissonClient, Clock.systemUTC())
    }

    @Bean
    @Profile("test | loadtest")
    fun noOpLoginAttemptLimiter(): LoginAttemptLimiter {
        return NoOpLoginAttemptLimiter()
    }
}
