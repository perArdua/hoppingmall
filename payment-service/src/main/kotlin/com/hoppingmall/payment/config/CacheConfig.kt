package com.hoppingmall.payment.config

import com.hoppingmall.cache.CachePolicy
import com.hoppingmall.cache.HotKeyDetectorRegistry
import com.hoppingmall.cache.LockProvider
import com.hoppingmall.cache.RedissonLockProvider
import com.hoppingmall.cache.TwoLevelCacheManager
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

@Configuration
@EnableCaching
@Profile("!test")
class CacheConfig {

    @Bean
    fun cachePolicies(): Map<String, CachePolicy> = mapOf(
        "point-balance" to CachePolicy(
            cacheName = "point-balance",
            l1MaxSize = 1000,
            l1Ttl = Duration.ofSeconds(30),
            l2Ttl = Duration.ofMinutes(30)
        ),
        "coupon:available" to CachePolicy(
            cacheName = "coupon:available",
            l1MaxSize = 100,
            l1Ttl = Duration.ofMinutes(5),
            l2Ttl = Duration.ofMinutes(30)
        ),
        "coupon:all" to CachePolicy(
            cacheName = "coupon:all",
            l1MaxSize = 100,
            l1Ttl = Duration.ofMinutes(5),
            l2Ttl = Duration.ofMinutes(30)
        ),
        "point-policy" to CachePolicy(
            cacheName = "point-policy",
            l1MaxSize = 10,
            l1Ttl = Duration.ofMinutes(30),
            l2Ttl = Duration.ofMinutes(30)
        )
    )

    @Bean
    fun hotKeyDetectorRegistry(
        cachePolicies: Map<String, CachePolicy>,
        @Value("\${cache.hot-key.detector-type:local}") detectorType: String,
        redissonClient: ObjectProvider<RedissonClient>
    ): HotKeyDetectorRegistry {
        return HotKeyDetectorRegistry(
            policies = cachePolicies.values,
            detectorType = detectorType,
            redissonClient = redissonClient.ifAvailable
        )
    }

    @Bean
    fun lockProvider(redissonClient: RedissonClient): LockProvider {
        return RedissonLockProvider(redissonClient)
    }

    @Bean
    fun redisCacheManager(
        connectionFactory: RedisConnectionFactory,
        cachePolicies: Map<String, CachePolicy>
    ): RedisCacheManager {
        return TwoLevelCacheManager.buildRedisCacheManager(connectionFactory, cachePolicies)
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
