package com.hoppingmall.notification.config

import com.hoppingmall.cache.CachePolicy
import com.hoppingmall.cache.CacheValueSerializer
import com.hoppingmall.notification.dto.response.UnreadCountResponse
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
        "unread-count" to CachePolicy(
            cacheName = "unread-count",
            l1MaxSize = 500,
            l1Ttl = Duration.ofSeconds(60),
            l2Ttl = Duration.ofMinutes(5),
            valueType = CacheValueSerializer.typeOf(UnreadCountResponse::class.java)
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
        return TwoLevelCacheManager.buildRedisCacheManager(
            connectionFactory, cachePolicies, defaultTtl = Duration.ofMinutes(5)
        )
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
