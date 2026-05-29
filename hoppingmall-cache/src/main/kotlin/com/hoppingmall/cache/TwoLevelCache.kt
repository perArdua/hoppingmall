package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Cache
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.cache.support.AbstractValueAdaptingCache
import org.springframework.cache.support.NullValue
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ln

/**
 * L1(Caffeine) + L2(Redis) 2단 캐시.
 *
 * 핫키(dynamicHotKeyEnabled, 현재 product) 경로는 **XFetch 논리 만료 + 비동기 갱신**으로 동작한다:
 * 핫키 읽기는 절대 블로킹하지 않는다 — CacheEntry가 물리적으로 있으면 즉시(약간 stale 가능) 값을 반환하고,
 * 논리 만료(XFetch 확률식)에 도달하면 백그라운드에서 클러스터 전체 1개 인스턴스만 갱신한다.
 *
 * 정합성: 갱신은 자기 L1만 갱신하고 타 인스턴스 L1은 TTL로 수렴한다(pub-sub 미도입).
 *   ⇒ 최대 staleness = L1 TTL(product 600s). 물리 L2 TTL(1800s)은 페일세이프(갱신 실패/크래시 시 그때까지 stale 서빙).
 *
 * 비핫 캐시는 CacheEntry로 래핑하지 않으며(wrapEntries=false) XFetch도 트리거되지 않는다 — 기존 동작 유지.
 */
class TwoLevelCache(
    private val name: String,
    private val caffeineCache: Cache<Any, Any>,
    private val redisCache: org.springframework.cache.Cache,
    private val policy: CachePolicy,
    private val refreshExecutor: Executor? = null,
    private val refreshGuard: RefreshGuard? = null,
    private val hotKeyDetector: HotKeyDetector? = null,
    meterRegistry: MeterRegistry? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    // (0,1] 균등난수. ThreadLocalRandom.nextDouble()는 [0,1)이므로 1.0 - x로 0을 제외한다(ln(0)=-inf 방지).
    private val random: () -> Double = { 1.0 - ThreadLocalRandom.current().nextDouble() }
) : AbstractValueAdaptingCache(true) {

    private val log = LoggerFactory.getLogger(TwoLevelCache::class.java)

    private val l1HitCounter = counter(meterRegistry, "cache.l1.hit")
    private val l2HitCounter = counter(meterRegistry, "cache.l2.hit")
    private val missCounter = counter(meterRegistry, "cache.miss")
    private val l2FailureCounter = counter(meterRegistry, "cache.l2.failure")
    private val singleFlightCollapsedCounter = counter(meterRegistry, "cache.singleflight.collapsed")
    private val refreshTriggeredCounter = counter(meterRegistry, "cache.xfetch.refresh.triggered")
    private val refreshSuccessCounter = counter(meterRegistry, "cache.xfetch.refresh.success")
    private val refreshFailureCounter = counter(meterRegistry, "cache.xfetch.refresh.failure")
    private val refreshDiscardedCounter = counter(meterRegistry, "cache.xfetch.refresh.discarded")
    private val staleServedCounter = counter(meterRegistry, "cache.xfetch.stale.served")

    // 하드미스 동기 로드 인스턴스내 dedup
    private val inFlightLoads = ConcurrentHashMap<Any, CompletableFuture<Any?>>()
    // XFetch 비동기 갱신 인스턴스내 dedup (키별 1개 태스크)
    private val refreshInFlight = ConcurrentHashMap.newKeySet<Any>()
    // evict 발생 횟수 — bg 갱신이 evict와 경합 시 stale 값 부활 방지(writeback 가드)
    private val evictEpoch = AtomicLong(0)

    // 이 캐시가 XFetch 래핑(CacheEntry) 대상인지. 현재 hotKeyThreshold>0(product)만 true.
    private val wrapEntries: Boolean = policy.dynamicHotKeyEnabled
    private val l2TtlMs: Long = policy.l2Ttl.toMillis()

    init {
        meterRegistry?.let {
            Gauge.builder("cache.singleflight.inflight", inFlightLoads) { m -> m.size.toDouble() }
                .tag("cache", name).register(it)
        }
    }

    private fun counter(reg: MeterRegistry?, metric: String): Counter? =
        reg?.let { Counter.builder(metric).tag("cache", name).register(it) }

    companion object {
        private const val XFETCH_BETA = 1.0
        private val REFRESH_GUARD_TTL = Duration.ofSeconds(3)
        private val REFRESH_FAIL_COOLDOWN = Duration.ofMillis(200)
        private const val SINGLE_FLIGHT_TIMEOUT_MS = 500L
        private const val REFRESH_GUARD_PREFIX = "refresh:"
    }

    override fun getName(): String = name
    override fun getNativeCache(): Any = caffeineCache

    // --- 읽기 ---

    /** sync=false 경로(@Cacheable 일반). 로더가 없으므로 갱신은 트리거하지 않고 unwrap만 한다. */
    override fun lookup(key: Any): Any? {
        caffeineCache.getIfPresent(key)?.let {
            l1HitCounter?.increment()
            return unwrap(it)
        }
        hotKeyDetector?.recordAccess(key.toString())
        val l2 = safeL2Get(key)
        if (l2 != null) {
            l2HitCounter?.increment()
            caffeineCache.put(key, l2)
            return unwrap(l2)
        }
        missCounter?.increment()
        return null
    }

    /** sync=true 경로(@Cacheable sync). 값이 있으면 즉시 반환(무블로킹) + XFetch 갱신 트리거. 하드미스만 동기 로드. */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? {
        caffeineCache.getIfPresent(key)?.let { stored ->
            l1HitCounter?.increment()
            maybeTriggerRefresh(key, stored, valueLoader)
            return fromStoreValue(unwrap(stored)) as T?
        }
        hotKeyDetector?.recordAccess(key.toString())

        val l2 = safeL2Get(key)
        if (l2 != null) {
            l2HitCounter?.increment()
            caffeineCache.put(key, l2)
            maybeTriggerRefresh(key, l2, valueLoader)
            return fromStoreValue(unwrap(l2)) as T?
        }

        // 하드미스: 서빙할 stale가 없으므로 동기 로드 불가피(인스턴스내 single-flight 1회).
        missCounter?.increment()
        return loadWithSingleFlight(key, valueLoader)
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
                log.warn("Single-flight 대기 초과 [cache={}, key={}], 직접 로드", name, key)
                loadAndStore(key, valueLoader)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                loadAndStore(key, valueLoader)
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
        return try {
            val result = loadAndStore(key, valueLoader)
            newFuture.complete(result)
            result
        } catch (e: Exception) {
            newFuture.completeExceptionally(e)
            throw e
        } finally {
            inFlightLoads.remove(key, newFuture)
        }
    }

    /** 로더 실행(소요시간 실측) → store. 반환은 언래핑된 사용자 값. */
    private fun <T : Any?> loadAndStore(key: Any, valueLoader: Callable<T>): T? {
        val t0 = clock()
        val loaded = valueLoader.call()
        store(key, loaded, clock() - t0)
        return loaded
    }

    // --- XFetch 갱신 ---

    private fun maybeTriggerRefresh(key: Any, stored: Any, valueLoader: Callable<*>?) {
        if (valueLoader == null) return
        val entry = stored as? CacheEntry<*> ?: return // 비래핑(비핫/콜드 NullValue)은 트리거 안 함
        if (!shouldRefresh(entry)) return
        if (!refreshInFlight.add(key)) return // 로컬 dedup: 이미 갱신 중
        val executor = refreshExecutor
        if (executor == null) {
            refreshInFlight.remove(key)
            return
        }
        try {
            executor.execute { doRefresh(key, valueLoader) }
            refreshTriggeredCounter?.increment()
            staleServedCounter?.increment()
        } catch (e: RejectedExecutionException) {
            refreshDiscardedCounter?.increment()
            refreshInFlight.remove(key)
        }
    }

    /** XFetch 확률식: now - delta*beta*ln(random) >= physicalExpireAt (random∈(0,1] → ln<=0). */
    private fun shouldRefresh(entry: CacheEntry<*>): Boolean {
        val now = clock().toDouble()
        val r = random().coerceIn(1e-12, 1.0)
        val gate = now - entry.lastLoadDurationMs * XFETCH_BETA * ln(r)
        return gate >= entry.physicalExpireAtEpochMs.toDouble()
    }

    private fun doRefresh(key: Any, valueLoader: Callable<*>) {
        val guardKey = "$REFRESH_GUARD_PREFIX$name:$key"
        try {
            val acquired = refreshGuard?.tryAcquire(guardKey, REFRESH_GUARD_TTL) ?: true
            if (!acquired) return // 다른 인스턴스가 갱신 중 → stale 서빙(이미 반환됨)
            val epochAtStart = evictEpoch.get()
            val loaded = try {
                valueLoader.call()
            } catch (e: Exception) {
                log.warn("XFetch 갱신 로드 실패 [cache={}, key={}]: {}", name, key, e.message)
                refreshFailureCounter?.increment()
                refreshGuard?.markFailed(guardKey, REFRESH_FAIL_COOLDOWN)
                return
            }
            // 갱신 도중 evict가 끼었으면 부활시키지 않는다.
            if (evictEpoch.get() != epochAtStart) return
            store(key, loaded, 0L) // 소요시간은 doRefresh 진입 전 측정 어려우므로 보수적으로 0; 다음 콜드로드가 정밀 갱신
            refreshSuccessCounter?.increment()
            // 성공 시 가드는 release하지 않고 TTL(3s)로 만료 → 같은 창 재갱신 방지
        } finally {
            refreshInFlight.remove(key)
        }
    }

    // --- 쓰기 ---

    override fun put(key: Any, value: Any?) {
        store(key, value, 0L)
    }

    /**
     * 저장. wrapEntries면 비-null 값을 CacheEntry로 감싸 메타데이터(now/physicalExpireAt/duration)를 채운다.
     * null은 래핑하지 않고 NullValue로 저장(타입드 직렬화기가 마커로 왕복) — null 캐싱 보존.
     */
    private fun store(key: Any, value: Any?, durationMs: Long) {
        val storeVal: Any = if (value == null) {
            NullValue.INSTANCE
        } else if (wrapEntries) {
            val now = clock()
            CacheEntry(toStoreValue(value), now, now + l2TtlMs, durationMs)
        } else {
            toStoreValue(value)
        }
        safeL2Put(key, storeVal)
        caffeineCache.put(key, storeVal)
    }

    override fun evict(key: Any) {
        evictEpoch.incrementAndGet()
        try {
            redisCache.evict(key)
        } catch (e: Exception) {
            log.warn("L2 evict 실패 [cache={}, key={}]: {}", name, key, e.message)
            l2FailureCounter?.increment()
        }
        caffeineCache.invalidate(key)
        refreshInFlight.remove(key)
    }

    override fun clear() {
        evictEpoch.incrementAndGet()
        try {
            redisCache.clear()
        } catch (e: Exception) {
            log.warn("L2 clear 실패 [cache={}]: {}", name, e.message)
            l2FailureCounter?.increment()
        }
        caffeineCache.invalidateAll()
    }

    // --- 내부 ---

    /** CacheEntry면 내부 값으로, 아니면 그대로(NullValue 포함). 이후 fromStoreValue가 NullValue→null 변환. */
    private fun unwrap(stored: Any?): Any? = if (stored is CacheEntry<*>) stored.value else stored

    private fun safeL2Get(key: Any): Any? = try {
        redisCache.get(key)?.get()
    } catch (e: Exception) {
        // 옛 포맷(직렬화 불일치) 등은 miss로 처리되어 재적재된다(자가치유).
        log.warn("L2 조회 실패 [cache={}, key={}]: {}", name, key, e.message)
        l2FailureCounter?.increment()
        null
    }

    private fun safeL2Put(key: Any, storeVal: Any) {
        try {
            redisCache.put(key, storeVal)
        } catch (e: Exception) {
            log.warn("L2 저장 실패 [cache={}, key={}]: {}", name, key, e.message)
            l2FailureCounter?.increment()
        }
    }
}
