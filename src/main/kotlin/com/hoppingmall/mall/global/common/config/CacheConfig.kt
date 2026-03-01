package com.hoppingmall.mall.global.common.config

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig(
    private val objectMapper: ObjectMapper
) : CachingConfigurer {

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .entryTtl(Duration.ofHours(1))

        val cacheConfigurations = mapOf(
            "category" to defaultConfig.entryTtl(Duration.ofMinutes(5)),
            "categories:root" to defaultConfig.entryTtl(Duration.ofMinutes(5)),
            "categories:sub" to defaultConfig.entryTtl(Duration.ofMinutes(5)),
            "product" to defaultConfig.entryTtl(Duration.ofMinutes(10))
        )

        val redisCacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build()

        val l1Specs = mapOf(
            "category" to L1CacheSpec(maxSize = 500, ttl = Duration.ofSeconds(30)),
            "categories:root" to L1CacheSpec(maxSize = 10, ttl = Duration.ofSeconds(60)),
            "categories:sub" to L1CacheSpec(maxSize = 200, ttl = Duration.ofSeconds(60)),
            "product" to L1CacheSpec(maxSize = 1000, ttl = Duration.ofSeconds(10))
        )

        return TwoLevelCacheManager(redisCacheManager, l1Specs)
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
