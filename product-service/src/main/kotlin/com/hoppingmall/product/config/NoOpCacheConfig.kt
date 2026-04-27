package com.hoppingmall.product.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@EnableCaching
@Profile("cache-disabled")
class NoOpCacheConfig {

    @Bean
    @Primary
    fun cacheManager(): CacheManager = NoOpCacheManager()
}
