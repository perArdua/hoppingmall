package com.hoppingmall.product.config

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
        val productCache = CaffeineCache(
            "product",
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build()
        )
        val inventoryCache = CaffeineCache(
            "inventory",
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build()
        )
        val categoryCache = CaffeineCache(
            "category",
            Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build()
        )
        val categoriesRootCache = CaffeineCache(
            "categories:root",
            Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build()
        )
        val categoriesSubCache = CaffeineCache(
            "categories:sub",
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build()
        )
        return SimpleCacheManager().apply {
            setCaches(listOf(productCache, inventoryCache, categoryCache, categoriesRootCache, categoriesSubCache))
        }
    }
}
