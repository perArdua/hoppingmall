package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Cache
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.cache.support.AbstractValueAdaptingCache
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TwoLevelCache(
    private val name: String,
    private val caffeineCache: Cache<Any, Any>,
    private val redisCache: org.springframework.cache.Cache,
    private val policy: CachePolicy,
    private val lockProvider: LockProvider,
    private val shardedRedisCache: org.springframework.cache.Cache? = null,
    private val hotKeyDetector: HotKeyDetector? = null,
    meterRegistry: MeterRegistry? = null
) : AbstractValueAdaptingCache(true) {

    private val log = LoggerFactory.getLogger(TwoLevelCache::class.java)
    private val l1HitCounter = meterRegistry?.let {
        Counter.builder("cache.l1.hit").tag("cache", name).register(it)
    }
    private val l2HitCounter = meterRegistry?.let {
        Counter.builder("cache.l2.hit").tag("cache", name).register(it)
    }
    private val missCounter = meterRegistry?.let {
        Counter.builder("cache.miss").tag("cache", name).register(it)
    }
    private val l2FailureCounter = meterRegistry?.let {
        Counter.builder("cache.l2.failure").tag("cache", name).register(it)
    }
    private val singleFlightCollapsedCounter = meterRegistry?.let {
        Counter.builder("cache.singleflight.collapsed").tag("cache", name).register(it)
    }

    private val inFlightLoads = ConcurrentHashMap<Any, CompletableFuture<Any?>>()

    init {
        meterRegistry?.let {
            Gauge.builder("cache.singleflight.inflight", inFlightLoads) { it.size.toDouble() }
                .tag("cache", name)
                .register(it)
        }
    }

    companion object {
        private const val LOCK_KEY_PREFIX = "lock:"
        private val LOCK_LEASE_TIME = Duration.ofSeconds(3)
        private const val LOCK_RETRY_INTERVAL_MS = 50L
        private const val LOCK_MAX_WAIT_MS = 500L
        private const val SINGLE_FLIGHT_TIMEOUT_MS = 500L
    }

    override fun getName(): String = name

    override fun getNativeCache(): Any = caffeineCache

    override fun lookup(key: Any): Any? {
        val l1Value = caffeineCache.getIfPresent(key)
        if (l1Value != null) {
            l1HitCounter?.increment()
            return l1Value
        }

        hotKeyDetector?.recordAccess(key.toString())
        val isHot = hotKeyDetector?.isHot(key.toString()) ?: false

        val l2Value = safeL2Get(key, isHot)
        if (l2Value != null) {
            l2HitCounter?.increment()
            caffeineCache.put(key, l2Value)
        } else {
            missCounter?.increment()
        }
        return l2Value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? {
        val l1Value = caffeineCache.getIfPresent(key)
        if (l1Value != null) {
            l1HitCounter?.increment()
            return fromStoreValue(l1Value) as T?
        }

        hotKeyDetector?.recordAccess(key.toString())
        val isHot = hotKeyDetector?.isHot(key.toString()) ?: false

        if (isHot && shardedRedisCache != null) {
            val shardValue = safeL2Get(key, true)
            if (shardValue != null) {
                l2HitCounter?.increment()
                caffeineCache.put(key, shardValue)
                return fromStoreValue(shardValue) as T?
            }
            return loadWithLock(key, valueLoader)
        }

        val l2Value = safeL2Get(key, false)
        if (l2Value != null) {
            l2HitCounter?.increment()
            caffeineCache.put(key, l2Value)
            return fromStoreValue(l2Value) as T?
        }

        missCounter?.increment()
        return loadWithSingleFlight(key, valueLoader)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any?> loadWithLock(key: Any, valueLoader: Callable<T>): T? {
        val lockKey = "$LOCK_KEY_PREFIX$name:$key"
        val locked = try {
            lockProvider.tryLock(lockKey, LOCK_LEASE_TIME)
        } catch (e: Exception) {
            log.warn("L2 lock 획득 실패 [cache={}, key={}]: {}", name, key, e.message)
            l2FailureCounter?.increment()
            false
        }

        if (locked) {
            try {
                val l2Recheck = safeL2Get(key, false)
                if (l2Recheck != null) {
                    l2HitCounter?.increment()
                    caffeineCache.put(key, l2Recheck)
                    return fromStoreValue(l2Recheck) as T?
                }
                missCounter?.increment()
                return loadAndCache(key, valueLoader)
            } finally {
                try {
                    lockProvider.unlock(lockKey)
                } catch (e: Exception) {
                    log.warn("L2 lock 해제 실패 [cache={}, key={}]: {}", name, key, e.message)
                }
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

            val l2Value = safeL2Get(key, false)
            if (l2Value != null) {
                l2HitCounter?.increment()
                caffeineCache.put(key, l2Value)
                return fromStoreValue(l2Value) as T?
            }
        }

        log.warn("Lock 대기 초과 [cache={}, key={}], DB 로더 직접 호출", name, key)
        missCounter?.increment()
        return loadAndCache(key, valueLoader)
    }

    private fun <T : Any?> loadAndCache(key: Any, valueLoader: Callable<T>): T? {
        val loaded = valueLoader.call()
        put(key, loaded)
        return loaded
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any?> loadWithSingleFlight(key: Any, valueLoader: Callable<T>): T? {
        val newFuture = CompletableFuture<Any?>()
        val existing = inFlightLoads.putIfAbsent(key, newFuture)

        if (existing != null) {
            singleFlightCollapsedCounter?.increment()
            return try {
                existing.get(SINGLE_FLIGHT_TIMEOUT_MS, TimeUnit.MILLISECONDS) as T?
            } catch (e: TimeoutException) {
                log.warn("Single-flight 대기 초과 [cache={}, key={}], DB 로더 직접 호출", name, key)
                loadAndCache(key, valueLoader)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                loadAndCache(key, valueLoader)
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }

        return try {
            val result = loadAndCache(key, valueLoader)
            newFuture.complete(result)
            result
        } catch (e: Exception) {
            newFuture.completeExceptionally(e)
            throw e
        } finally {
            inFlightLoads.remove(key, newFuture)
        }
    }

    override fun put(key: Any, value: Any?) {
        val isHot = hotKeyDetector?.isHot(key.toString()) ?: false

        if (value != null) {
            safeL2Put(key, value, isHot)
            caffeineCache.put(key, toStoreValue(value))
        } else {
            safeL2Put(key, value, isHot)
            caffeineCache.invalidate(key)
        }
    }

    override fun evict(key: Any) {
        val isHot = hotKeyDetector?.isHot(key.toString()) ?: false

        try {
            if (isHot && shardedRedisCache != null) {
                shardedRedisCache.evict(key)
            } else {
                redisCache.evict(key)
            }
        } catch (e: Exception) {
            log.warn("L2 evict 실패 [cache={}, key={}]: {}", name, key, e.message)
            l2FailureCounter?.increment()
        }
        caffeineCache.invalidate(key)
    }

    override fun clear() {
        try {
            redisCache.clear()
        } catch (e: Exception) {
            log.warn("L2 clear 실패 [cache={}]: {}", name, e.message)
            l2FailureCounter?.increment()
        }
        caffeineCache.invalidateAll()
    }

    private fun safeL2Get(key: Any, isHot: Boolean): Any? {
        return try {
            val l2Cache = if (isHot && shardedRedisCache != null) shardedRedisCache else redisCache
            l2Cache.get(key)?.get()
        } catch (e: Exception) {
            log.warn("L2 조회 실패 [cache={}, key={}]: {}", name, key, e.message)
            l2FailureCounter?.increment()
            null
        }
    }

    private fun safeL2Put(key: Any, value: Any?, isHot: Boolean) {
        try {
            if (isHot && shardedRedisCache != null) {
                shardedRedisCache.put(key, value)
            } else {
                redisCache.put(key, value)
            }
        } catch (e: Exception) {
            log.warn("L2 저장 실패 [cache={}, key={}]: {}", name, key, e.message)
            l2FailureCounter?.increment()
        }
    }
}
