package com.hoppingmall.mall.global.common.config

import com.github.benmanes.caffeine.cache.Cache
import com.hoppingmall.mall.global.common.config.cache.CachePolicy
import com.hoppingmall.mall.global.common.config.cache.LockProvider
import org.slf4j.LoggerFactory
import org.springframework.cache.support.AbstractValueAdaptingCache
import java.time.Duration
import java.util.concurrent.Callable

class TwoLevelCache(
    private val name: String,
    private val caffeineCache: Cache<Any, Any>,
    private val redisCache: org.springframework.cache.Cache,
    private val policy: CachePolicy,
    private val lockProvider: LockProvider
) : AbstractValueAdaptingCache(true) {

    private val log = LoggerFactory.getLogger(TwoLevelCache::class.java)

    companion object {
        private const val LOCK_KEY_PREFIX = "lock:"
        private val LOCK_LEASE_TIME = Duration.ofSeconds(3)
        private const val LOCK_RETRY_INTERVAL_MS = 80L
        private const val LOCK_MAX_WAIT_MS = 1500L
    }

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

        val l2Value = redisCache.get(key)?.get()
        if (l2Value != null) {
            caffeineCache.put(key, l2Value)
            return fromStoreValue(l2Value) as T?
        }

        if (!policy.hotKey) {
            return loadAndCache(key, valueLoader)
        }

        return loadWithLock(key, valueLoader)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any?> loadWithLock(key: Any, valueLoader: Callable<T>): T? {
        val lockKey = "$LOCK_KEY_PREFIX$name:$key"
        val locked = lockProvider.tryLock(lockKey, LOCK_LEASE_TIME)

        if (locked) {
            try {
                val l2Recheck = redisCache.get(key)?.get()
                if (l2Recheck != null) {
                    caffeineCache.put(key, l2Recheck)
                    return fromStoreValue(l2Recheck) as T?
                }
                return loadAndCache(key, valueLoader)
            } finally {
                lockProvider.unlock(lockKey)
            }
        }

        return waitForCacheOrFallback(key, valueLoader)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any?> waitForCacheOrFallback(key: Any, valueLoader: Callable<T>): T? {
        var waited = 0L
        while (waited < LOCK_MAX_WAIT_MS) {
            Thread.sleep(LOCK_RETRY_INTERVAL_MS)
            waited += LOCK_RETRY_INTERVAL_MS

            val l2Value = redisCache.get(key)?.get()
            if (l2Value != null) {
                caffeineCache.put(key, l2Value)
                return fromStoreValue(l2Value) as T?
            }
        }

        log.warn("Lock 대기 초과 [cache={}, key={}], DB 로더 직접 호출", name, key)
        return loadAndCache(key, valueLoader)
    }

    private fun <T : Any?> loadAndCache(key: Any, valueLoader: Callable<T>): T? {
        val loaded = valueLoader.call()
        put(key, loaded)
        return loaded
    }

    override fun put(key: Any, value: Any?) {
        if (value != null) {
            redisCache.put(key, value)
            caffeineCache.put(key, toStoreValue(value))
        } else {
            redisCache.put(key, value)
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
