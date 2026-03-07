package com.hoppingmall.mall.global.common.config.ratelimit

import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@Profile("!test")
class RateLimitConfig(
    private val redissonClient: RedissonClient
) : WebMvcConfigurer {

    @Bean
    fun rateLimitProxyManager(): ProxyManager<String> {
        val commandExecutor = (redissonClient as Redisson).commandExecutor
        return RedissonBasedProxyManager.builderFor(commandExecutor)
            .build()
    }

    @Bean
    fun rateLimitInterceptor(): RateLimitInterceptor {
        return RateLimitInterceptor(rateLimitProxyManager())
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        val paths = RateLimitPolicy.POLICIES.map { it.pathPattern }.distinct().toTypedArray()
        registry.addInterceptor(rateLimitInterceptor())
            .addPathPatterns(*paths)
    }
}
