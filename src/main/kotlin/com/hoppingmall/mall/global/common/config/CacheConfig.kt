package com.hoppingmall.mall.global.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.config.cache.CachePolicy
import com.hoppingmall.mall.global.common.config.cache.LockProvider
import com.hoppingmall.mall.global.common.config.cache.RedisLockProvider
import com.hoppingmall.mall.global.common.config.cache.TtlJitter
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig(
    private val objectMapper: ObjectMapper
) : CachingConfigurer {

    companion object {
        val CACHE_POLICIES = listOf(
            CachePolicy("category", l1MaxSize = 500, l1Ttl = Duration.ofSeconds(30), l2Ttl = Duration.ofMinutes(5), jitterPercent = 10),
            CachePolicy("category:notfound", l1MaxSize = 200, l1Ttl = Duration.ofSeconds(5), l2Ttl = Duration.ofSeconds(15), jitterPercent = 10),
            CachePolicy("categories:root", l1MaxSize = 10, l1Ttl = Duration.ofSeconds(60), l2Ttl = Duration.ofMinutes(5), jitterPercent = 10),
            CachePolicy("categories:sub", l1MaxSize = 200, l1Ttl = Duration.ofSeconds(60), l2Ttl = Duration.ofMinutes(5), jitterPercent = 10),
            CachePolicy("product", l1MaxSize = 1000, l1Ttl = Duration.ofSeconds(10), l2Ttl = Duration.ofMinutes(10), jitterPercent = 10, hotKey = true),
            CachePolicy("product:notfound", l1MaxSize = 500, l1Ttl = Duration.ofSeconds(5), l2Ttl = Duration.ofSeconds(20), jitterPercent = 10)
        )
    }

    @Bean
    fun cacheLockProvider(connectionFactory: RedisConnectionFactory): LockProvider {
        return RedisLockProvider(StringRedisTemplate(connectionFactory))
    }

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory, cacheLockProvider: LockProvider): CacheManager {
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .entryTtl(Duration.ofHours(1))

        val cacheConfigurations = CACHE_POLICIES.associate { policy ->
            policy.cacheName to defaultConfig.entryTtl(TtlJitter.apply(policy.l2Ttl, policy.jitterPercent))
        }

        val redisCacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build()

        val policyMap = CACHE_POLICIES.associateBy { it.cacheName }

        return TwoLevelCacheManager(redisCacheManager, policyMap, cacheLockProvider)
    }

    override fun errorHandler(): CacheErrorHandler {
        return RedisCacheErrorHandler()
    }

    class RedisCacheErrorHandler : CacheErrorHandler {

        private val log = LoggerFactory.getLogger(RedisCacheErrorHandler::class.java)

        override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) {
            log.warn("Cache GET 실패 [cache={}, key={}]: {}", cache.name, key, exception.message)
        }

        override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) {
            log.warn("Cache PUT 실패 [cache={}, key={}]: {}", cache.name, key, exception.message)
        }

        override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) {
            log.warn("Cache EVICT 실패 [cache={}, key={}]: {}", cache.name, key, exception.message)
        }

        override fun handleCacheClearError(exception: RuntimeException, cache: Cache) {
            log.warn("Cache CLEAR 실패 [cache={}]: {}", cache.name, exception.message)
        }
    }
}
