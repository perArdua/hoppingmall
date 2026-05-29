package com.hoppingmall.product.config

import com.hoppingmall.cache.CachePolicy
import com.hoppingmall.cache.CacheValueSerializer
import com.hoppingmall.product.category.dto.response.CategoryResponse
import com.hoppingmall.product.inventory.dto.response.InventoryResponse
import com.hoppingmall.product.product.dto.response.ProductResponse
import com.hoppingmall.cache.HotKeyDetectorRegistry
import com.hoppingmall.cache.RefreshGuard
import com.hoppingmall.cache.RedissonRefreshGuard
import com.hoppingmall.cache.TwoLevelCacheManager
import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

@Configuration
@EnableCaching
@Profile("!test & !cache-disabled")
class CacheConfig {

    @Bean
    fun cachePolicies(
        @Value("\${cache.product.l1-ttl-seconds:600}") productL1TtlSec: Long,
        @Value("\${cache.product.l2-ttl-seconds:1800}") productL2TtlSec: Long
    ): Map<String, CachePolicy> = mapOf(
        "product" to CachePolicy(
            cacheName = "product",
            l1MaxSize = 500,
            l1Ttl = Duration.ofSeconds(productL1TtlSec),
            l2Ttl = Duration.ofSeconds(productL2TtlSec),
            hotKeyThreshold = 50,
            hotKeyWindow = Duration.ofSeconds(60),
            valueType = CacheValueSerializer.entryOf(ProductResponse::class.java)
        ),
        "inventory" to CachePolicy(
            cacheName = "inventory",
            l1MaxSize = 500,
            l1Ttl = Duration.ofMinutes(5),
            l2Ttl = Duration.ofMinutes(15),
            valueType = CacheValueSerializer.typeOf(InventoryResponse::class.java)
        ),
        "category" to CachePolicy(
            cacheName = "category",
            l1MaxSize = 200,
            l1Ttl = Duration.ofMinutes(60),
            l2Ttl = Duration.ofMinutes(120),
            valueType = CacheValueSerializer.typeOf(CategoryResponse::class.java)
        ),
        "categories:root" to CachePolicy(
            cacheName = "categories:root",
            l1MaxSize = 1,
            l1Ttl = Duration.ofMinutes(60),
            l2Ttl = Duration.ofMinutes(120),
            valueType = CacheValueSerializer.listOf(CategoryResponse::class.java)
        ),
        "categories:sub" to CachePolicy(
            cacheName = "categories:sub",
            l1MaxSize = 100,
            l1Ttl = Duration.ofMinutes(60),
            l2Ttl = Duration.ofMinutes(120),
            valueType = CacheValueSerializer.listOf(CategoryResponse::class.java)
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
    fun refreshGuard(redissonClient: RedissonClient): RefreshGuard {
        return RedissonRefreshGuard(redissonClient)
    }

    @Bean
    fun redisCacheManager(
        connectionFactory: RedisConnectionFactory,
        cachePolicies: Map<String, CachePolicy>
    ): RedisCacheManager {
        return TwoLevelCacheManager.buildRedisCacheManager(connectionFactory, cachePolicies)
    }

    @Bean
    @Primary
    fun cacheManager(
        redisCacheManager: RedisCacheManager,
        cachePolicies: Map<String, CachePolicy>,
        refreshGuard: RefreshGuard,
        hotKeyDetectorRegistry: HotKeyDetectorRegistry,
        meterRegistry: MeterRegistry
    ): CacheManager {
        return TwoLevelCacheManager(
            redisCacheManager = redisCacheManager,
            policies = cachePolicies,
            hotKeyDetectorRegistry = hotKeyDetectorRegistry,
            refreshGuard = refreshGuard,
            meterRegistry = meterRegistry
        )
    }
}
