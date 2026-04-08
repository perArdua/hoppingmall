package com.hoppingmall.payment.support

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.TimeUnit

@TestConfiguration
@EnableCaching
class TestCacheConfig {

    @Bean
    @Primary
    fun cacheManager(): CacheManager {
        val caches = listOf("point-balance", "coupon:available", "coupon:all", "point-policy").map { name ->
            CaffeineCache(
                name,
                Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .build()
            )
        }
        return SimpleCacheManager().apply { setCaches(caches) }
    }
}
