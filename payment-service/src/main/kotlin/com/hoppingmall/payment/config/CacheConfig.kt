package com.hoppingmall.payment.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val pointBalanceCache = CaffeineCache(
            "point-balance",
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build()
        )
        val couponAvailableCache = CaffeineCache(
            "coupon:available",
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build()
        )
        val couponAllCache = CaffeineCache(
            "coupon:all",
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build()
        )
        val pointPolicyCache = CaffeineCache(
            "point-policy",
            Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build()
        )
        return SimpleCacheManager().apply {
            setCaches(listOf(pointBalanceCache, couponAvailableCache, couponAllCache, pointPolicyCache))
        }
    }
}
