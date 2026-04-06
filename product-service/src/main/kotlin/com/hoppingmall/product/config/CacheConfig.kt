package com.hoppingmall.product.config

import com.hoppingmall.cache.CachePolicy
import com.hoppingmall.cache.HotKeyDetectorRegistry
import com.hoppingmall.cache.LockProvider
import com.hoppingmall.cache.RedissonLockProvider
import com.hoppingmall.cache.TwoLevelCacheManager
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

@Configuration
@EnableCaching
@Profile("!test")
class CacheConfig {

    @Bean
    fun cachePolicies(): Map<String, CachePolicy> = mapOf(
        "product" to CachePolicy(
            cacheName = "product",
            l1MaxSize = 500,
            l1Ttl = Duration.ofMinutes(10),
            l2Ttl = Duration.ofMinutes(30),
            hotKeyThreshold = 50,
            hotKeyWindow = Duration.ofSeconds(60)
        ),
        "inventory" to CachePolicy(
            cacheName = "inventory",
            l1MaxSize = 500,
            l1Ttl = Duration.ofMinutes(5),
            l2Ttl = Duration.ofMinutes(15)
        ),
        "category" to CachePolicy(
            cacheName = "category",
            l1MaxSize = 200,
            l1Ttl = Duration.ofMinutes(60),
            l2Ttl = Duration.ofMinutes(120)
        ),
        "categories:root" to CachePolicy(
            cacheName = "categories:root",
            l1MaxSize = 1,
            l1Ttl = Duration.ofMinutes(60),
            l2Ttl = Duration.ofMinutes(120)
        ),
        "categories:sub" to CachePolicy(
            cacheName = "categories:sub",
            l1MaxSize = 100,
            l1Ttl = Duration.ofMinutes(60),
            l2Ttl = Duration.ofMinutes(120)
        )
    )

    @Bean
    fun hotKeyDetectorRegistry(cachePolicies: Map<String, CachePolicy>): HotKeyDetectorRegistry {
        return HotKeyDetectorRegistry(cachePolicies.values)
    }

    @Bean
    fun lockProvider(redissonClient: RedissonClient): LockProvider {
        return RedissonLockProvider(redissonClient)
    }

    @Bean
    fun redisCacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .build()
    }

    @Bean
    fun cacheManager(
        redisCacheManager: RedisCacheManager,
        cachePolicies: Map<String, CachePolicy>,
        lockProvider: LockProvider,
        hotKeyDetectorRegistry: HotKeyDetectorRegistry,
        meterRegistry: MeterRegistry
    ): CacheManager {
        return TwoLevelCacheManager(
            redisCacheManager = redisCacheManager,
            policies = cachePolicies,
            lockProvider = lockProvider,
            hotKeyDetectorRegistry = hotKeyDetectorRegistry,
            meterRegistry = meterRegistry
        )
    }
}
