package com.hoppingmall.mall.global.common.config

import com.github.benmanes.caffeine.cache.Cache
import org.springframework.cache.support.AbstractValueAdaptingCache
import java.util.concurrent.Callable

class TwoLevelCache(
    private val name: String,
    private val caffeineCache: Cache<Any, Any>,
    private val redisCache: org.springframework.cache.Cache
) : AbstractValueAdaptingCache(true) {

    override fun getName(): String = name

    override fun getNativeCache(): Any = caffeineCache

    override fun lookup(key: Any): Any? {
        val l1Value = caffeineCache.getIfPresent(key)
        if (l1Value != null) return l1Value

        val l2Value = redisCache.get(key)?.get()
        if (l2Value != null) {
            caffeineCache.put(key, l2Value)
        }
        return l2Value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? {
        val l1Value = caffeineCache.getIfPresent(key)
        if (l1Value != null) return fromStoreValue(l1Value) as T?

        val l2Value = redisCache.get(key, valueLoader)
        if (l2Value != null) {
            caffeineCache.put(key, toStoreValue(l2Value))
        }
        return l2Value
    }

    override fun put(key: Any, value: Any?) {
        redisCache.put(key, value)
        if (value != null) {
            caffeineCache.put(key, toStoreValue(value))
        } else {
            caffeineCache.invalidate(key)
        }
    }

    override fun evict(key: Any) {
        redisCache.evict(key)
        caffeineCache.invalidate(key)
    }

    override fun clear() {
        redisCache.clear()
        caffeineCache.invalidateAll()
    }
}
