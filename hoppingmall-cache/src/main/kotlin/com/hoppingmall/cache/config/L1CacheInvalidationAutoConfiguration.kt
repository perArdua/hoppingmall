package com.hoppingmall.cache.config

import com.hoppingmall.cache.L1CacheInvalidationRegistrar
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(RedissonClient::class)
@ConditionalOnProperty(
    prefix = "hoppingmall.cache.l1-invalidation",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class L1CacheInvalidationAutoConfiguration {

    @Bean
    fun l1CacheInvalidationRegistrar(
        cacheManagers: ObjectProvider<CacheManager>,
        redissonClient: ObjectProvider<RedissonClient>,
        @Value("\${spring.application.name:application}") applicationName: String
    ): L1CacheInvalidationRegistrar =
        L1CacheInvalidationRegistrar(cacheManagers, redissonClient, "cache:l1-invalidation:$applicationName")
}
