package com.hoppingmall.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.RejectedExecutionException
import org.springframework.cache.concurrent.ConcurrentMapCache
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * XFetch(논리 만료 + 무블로킹 백그라운드 갱신) 결정론 검증.
 *
 * 결정론 도구:
 * - clock 주입: 고정/제어 가능한 가상 시계로 물리 만료/논리 만료 시점을 정확히 통제.
 * - random 주입: shouldRefresh 확률식을 고정해 갱신 트리거 여부를 강제(true/false).
 * - 동기 Executor( { it.run() } ): 갱신을 호출 스레드에서 즉시 실행 → 비동기 타이밍 없이 카운팅.
 * - L2는 ConcurrentMapCache(임베디드 redis 불필요), refreshGuard=null(단일 인스턴스).
 */
@DisplayName("TwoLevelCache XFetch")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TwoLevelCacheXFetchTest {

    private lateinit var meterRegistry: SimpleMeterRegistry

    private val hotPolicy = CachePolicy(
        cacheName = "product",
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(600),
        l2Ttl = Duration.ofMillis(1000), // physicalExpireAt = cachedAt + 1000ms
        hotKeyThreshold = 50L
    )

    private val coldPolicy = CachePolicy(
        cacheName = "category",
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(600),
        l2Ttl = Duration.ofMillis(1000)
    )

    private val sameThreadExecutor = Executor { it.run() }

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
    }

    private fun cache(
        policy: CachePolicy,
        clock: () -> Long,
        random: () -> Double = { 1.0 },
        executor: Executor? = sameThreadExecutor,
        refreshGuard: RefreshGuard? = null,
        hotKeyDetector: HotKeyDetector? = null,
        l2: org.springframework.cache.Cache = ConcurrentMapCache(policy.cacheName)
    ): TwoLevelCache {
        val caffeine = Caffeine.newBuilder()
            .maximumSize(policy.l1MaxSize)
            .expireAfterWrite(policy.l1Ttl)
            .build<Any, Any>()
        return TwoLevelCache(
            name = policy.cacheName,
            caffeineCache = caffeine,
            redisCache = l2,
            policy = policy,
            refreshExecutor = executor,
            refreshGuard = refreshGuard,
            hotKeyDetector = hotKeyDetector,
            meterRegistry = meterRegistry,
            clock = clock,
            random = random
        )
    }

    private fun entryAt(l2: org.springframework.cache.Cache, key: String): CacheEntry<*> =
        l2.get(key)?.get() as CacheEntry<*>

    private fun refreshTriggered() = meterRegistry.counter("cache.xfetch.refresh.triggered", "cache", "product").count()
    private fun refreshSuccess() = meterRegistry.counter("cache.xfetch.refresh.success", "cache", "product").count()
    private fun refreshFailure() = meterRegistry.counter("cache.xfetch.refresh.failure", "cache", "product").count()
    private fun staleServed() = meterRegistry.counter("cache.xfetch.stale.served", "cache", "product").count()

    @Nested
    @DisplayName("논리 만료 시 stale 즉시 반환 + 갱신 트리거")
    inner class StaleServeAndRefresh {

        @Test
        fun 논리_만료_시_get은_stale을_반환하고_갱신을_트리거한다() {
            val now = AtomicLong(0L)
            // r을 아주 작게 → -ln(r) 큰 양수 → gate = now - delta*ln(r) 가 physicalExpireAt를 초과 → shouldRefresh=true
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 })
            val loads = AtomicInteger(0)
            val loader = Callable {
                loads.incrementAndGet()
                "v${loads.get()}"
            }

            // 1) 콜드 적재: physicalExpireAt = 0 + 1000 = 1000, lastLoadDuration = 0
            //    duration 0이면 gate=now라 트리거 안 되니, 약간의 로드시간을 갖도록 clock을 진행시킨다.
            now.set(0L)
            val v1 = cache.get("k", Callable {
                loads.incrementAndGet()
                now.addAndGet(100L) // 로드에 100ms 소요 → lastLoadDurationMs=100 (XFetch delta)
                "v1"
            })
            assertThat(v1).isEqualTo("v1")
            assertThat(loads.get()).isEqualTo(1)

            // 2) 물리 만료 전(now=500 < 1010) 재조회: stale 'v1' 즉시 반환 + 갱신 트리거(동기 executor로 즉시 로드)
            now.set(500L)
            val v2 = cache.get("k", loader)

            assertThat(v2).isEqualTo("v1") // stale 즉시 반환(갱신 결과가 아님)
            assertThat(loads.get()).isEqualTo(2) // 갱신 로더 1회 추가 호출
            assertThat(refreshTriggered()).isEqualTo(1.0)
            assertThat(refreshSuccess()).isEqualTo(1.0)
            assertThat(staleServed()).isEqualTo(1.0)
        }

        @Test
        fun 논리_만료_전이면_갱신을_트리거하지_않는다() {
            val now = AtomicLong(0L)
            // r=1.0 → ln(1)=0 → gate=now. now < physicalExpireAt 이면 false.
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1.0 })
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); "v1" })
            assertThat(loads.get()).isEqualTo(1)

            now.set(100L) // 물리 만료(1000) 한참 전, gate=100 < 1000
            val v = cache.get("k", Callable { loads.incrementAndGet(); "v2" })

            assertThat(v).isEqualTo("v1")
            assertThat(loads.get()).isEqualTo(1) // 갱신 없음
            assertThat(refreshTriggered()).isEqualTo(0.0)
        }
    }

    @Nested
    @DisplayName("인스턴스내 갱신 dedup")
    inner class RefreshDedup {

        @Test
        fun 동시_N_스레드_논리만료_get은_백그라운드_갱신을_1회만_수행한다() {
            val now = AtomicLong(0L)
            // 큐에 쌓아두고 수동으로 비우지 않는 executor → refreshInFlight 가 add된 채 유지되어 dedup 관찰
            val tasks = ConcurrentLinkedQueue<Runnable>()
            val queuingExecutor = Executor { tasks.add(it) }
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, executor = queuingExecutor)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })
            assertThat(loads.get()).isEqualTo(1)

            now.set(500L)
            val threadCount = 20
            val barrier = CyclicBarrier(threadCount)
            val latch = CountDownLatch(threadCount)
            val pool = Executors.newFixedThreadPool(threadCount)
            val results = ConcurrentLinkedQueue<Any?>()
            repeat(threadCount) {
                pool.submit {
                    try {
                        barrier.await()
                        results.add(cache.get("k", Callable { loads.incrementAndGet(); "refreshed" }))
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
            pool.shutdown()

            // 모든 스레드는 stale 'v1' 즉시 반환. 갱신 태스크는 1개만 큐에 적재(dedup).
            assertThat(results).allMatch { it == "v1" }
            assertThat(tasks.size).isEqualTo(1)
            assertThat(loads.get()).isEqualTo(1) // 갱신 로더는 아직 실행 전(큐에만 존재)

            // 큐의 단일 태스크 실행 → 정확히 1회 갱신
            tasks.poll()!!.run()
            assertThat(loads.get()).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("인스턴스간 갱신 dedup (RefreshGuard)")
    inner class CrossInstanceDedup {

        @Test
        fun tryAcquire_false면_갱신_로드를_수행하지_않는다() {
            val now = AtomicLong(0L)
            val guard = mock<RefreshGuard>()
            whenever(guard.tryAcquire(any(), any())).thenReturn(false)
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, refreshGuard = guard)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })
            now.set(500L)
            val v = cache.get("k", Callable { loads.incrementAndGet(); "refreshed" })

            assertThat(v).isEqualTo("v1")
            assertThat(loads.get()).isEqualTo(1) // 가드 미획득 → 갱신 로드 없음
            verify(guard).tryAcquire(any(), any())
            // 메트릭은 가드 획득(=실제 갱신 주도) 이후에만 증가하므로, 가드 패배 시 triggered/success 모두 0.
            assertThat(refreshTriggered()).isEqualTo(0.0)
            assertThat(refreshSuccess()).isEqualTo(0.0)
        }

        @Test
        fun tryAcquire_true면_갱신_로드를_1회_수행한다() {
            val now = AtomicLong(0L)
            val guard = mock<RefreshGuard>()
            whenever(guard.tryAcquire(any(), any())).thenReturn(true)
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, refreshGuard = guard)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })
            now.set(500L)
            val v = cache.get("k", Callable { loads.incrementAndGet(); "refreshed" })

            assertThat(v).isEqualTo("v1")
            assertThat(loads.get()).isEqualTo(2)
            assertThat(refreshSuccess()).isEqualTo(1.0)
        }
    }

    @Nested
    @DisplayName("갱신 실패 페일세이프")
    inner class RefreshFailFailsafe {

        @Test
        fun 갱신_로더_예외_시_stale을_유지하고_markFailed를_호출한다() {
            val now = AtomicLong(0L)
            val guard = mock<RefreshGuard>()
            whenever(guard.tryAcquire(any(), any())).thenReturn(true)
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, refreshGuard = guard)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })

            now.set(500L)
            val v = cache.get("k", Callable<String> {
                loads.incrementAndGet()
                throw IllegalStateException("refresh boom")
            })

            assertThat(v).isEqualTo("v1") // 예외 전파 없이 stale 유지
            assertThat(refreshFailure()).isEqualTo(1.0)
            verify(guard).markFailed(any(), eq(Duration.ofMillis(200)))

            // 물리 만료 전까지 다음 get도 stale 'v1' 서빙(L1/L2 그대로) — random=1.0으로 재트리거 막고 확인
            now.set(600L)
            // 동일 cache는 random이 1e-6라 또 트리거되지만, 로더가 또 던져도 stale 유지됨을 확인
            val v2 = cache.get("k", Callable<String> { loads.incrementAndGet(); throw IllegalStateException("again") })
            assertThat(v2).isEqualTo("v1")
        }
    }

    @Nested
    @DisplayName("무블로킹 불변식")
    inner class NonBlocking {

        @Test
        @Timeout(5)
        fun 갱신_로더가_latch로_막혀도_get은_stale을_즉시_반환한다() {
            val now = AtomicLong(0L)
            val asyncPool = Executors.newSingleThreadExecutor()
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, executor = asyncPool)

            val loaderEntered = CountDownLatch(1)
            val releaseLoader = CountDownLatch(1)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })

            now.set(500L)
            // 갱신 로더는 releaseLoader가 풀릴 때까지 블로킹. get()은 그것과 무관하게 즉시 stale 반환해야 한다.
            val v = cache.get("k", Callable {
                loads.incrementAndGet()
                loaderEntered.countDown()
                releaseLoader.await() // 영원히 대기(테스트 끝까지)
                "refreshed"
            })

            // 결정론: get()이 반환된 시점에 갱신 로더는 아직 latch에 갇혀 있다(해제 전).
            assertThat(v).isEqualTo("v1")
            assertThat(loaderEntered.await(2, TimeUnit.SECONDS)).isTrue() // bg 로더는 시작은 됨
            assertThat(releaseLoader.count).isEqualTo(1L) // 아직 해제 안 함 → get은 로더 완료를 기다리지 않았음

            releaseLoader.countDown()
            asyncPool.shutdown()
            assertThat(asyncPool.awaitTermination(3, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Nested
    @DisplayName("비핫 캐시 회귀 방지")
    inner class ColdCacheRegression {

        @Test
        fun 비핫_캐시는_CacheEntry_래핑도_XFetch_트리거도_하지_않는다() {
            val now = AtomicLong(0L)
            val l2 = ConcurrentMapCache("category")
            val cache = cache(coldPolicy, clock = { now.get() }, random = { 1e-6 }, l2 = l2)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })

            // L2 저장형이 CacheEntry가 아니어야 함
            assertThat(l2.get("k")?.get()).isEqualTo("v1")

            now.set(500L)
            val v = cache.get("k", Callable { loads.incrementAndGet(); "v2" })

            assertThat(v).isEqualTo("v1")
            assertThat(loads.get()).isEqualTo(1) // XFetch 트리거 안 됨
            assertThat(meterRegistry.find("cache.xfetch.refresh.triggered").counter()?.count() ?: 0.0).isEqualTo(0.0)
        }
    }

    @Nested
    @DisplayName("버그1: 갱신 시 lastLoadDuration 실측")
    inner class RefreshMeasuresDuration {
        @Test
        fun 갱신은_duration을_실측해_저장하고_물리만료_전_조기갱신이_지속된다() {
            val now = AtomicLong(0L)
            val l2 = ConcurrentMapCache("product")
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, l2 = l2)
            val loads = AtomicInteger(0)

            // 콜드 적재: now 0→100 → duration=100, physicalExpireAt=100+1000=1100
            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })
            assertThat(entryAt(l2, "k").lastLoadDurationMs).isEqualTo(100L)

            // now=600(<1100): 갱신 트리거, 갱신 로더도 50ms 소요
            now.set(600L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(50L); "v2" })
            val refreshed = entryAt(l2, "k")
            assertThat(refreshed.lastLoadDurationMs).isEqualTo(50L) // 버그1 핵심: 0이 아닌 실측
            assertThat(refreshed.value).isEqualTo("v2")
            assertThat(loads.get()).isEqualTo(2)

            // 2차 갱신 store는 now=650 → physicalExpireAt=1650. duration=50>0 덕에 now=1000(<1650)에도 조기 트리거.
            // (duration=0이었다면 gate=now=1000<1650 → 트리거 안 됨 = 회귀 가드)
            now.set(1000L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(50L); "v3" })
            assertThat(loads.get()).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("버그2: 제출 거부 시 refreshInFlight 비잔류")
    inner class RejectionCleansInFlight {
        @Test
        fun 갱신_제출_거부시_키가_잔류하지_않고_다음_읽기에서_재트리거된다() {
            val now = AtomicLong(0L)
            val attempts = AtomicInteger(0)
            val rejecting = Executor { attempts.incrementAndGet(); throw RejectedExecutionException("full") }
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, executor = rejecting)

            now.set(0L)
            cache.get("k", Callable { now.addAndGet(100L); "v1" }) // 콜드 적재(동기, executor 무관)

            now.set(600L)
            assertThat(cache.get("k", Callable { "x" })).isEqualTo("v1") // 트리거 → 거부(attempt1)
            assertThat(cache.get("k", Callable { "x" })).isEqualTo("v1") // 다시 트리거 → 거부(attempt2)

            assertThat(attempts.get()).isEqualTo(2) // 잔류했다면 add 실패로 2번째 제출이 없었을 것
            assertThat(meterRegistry.find("cache.xfetch.refresh.discarded").counter()?.count() ?: 0.0).isEqualTo(2.0)
        }
    }

    @Nested
    @DisplayName("버그3: 감지기 게이팅")
    inner class DetectorGating {
        @Test
        fun isHot_false면_논리만료여도_갱신을_트리거하지_않는다() {
            val now = AtomicLong(0L)
            val detector = mock<HotKeyDetector>()
            whenever(detector.isHot(any())).thenReturn(false)
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, hotKeyDetector = detector)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })
            now.set(600L)
            cache.get("k", Callable { loads.incrementAndGet(); "v2" })

            assertThat(loads.get()).isEqualTo(1) // 콜드만, 갱신 없음
            assertThat(refreshTriggered()).isEqualTo(0.0)
        }

        @Test
        fun isHot_true면_논리만료시_갱신을_트리거한다() {
            val now = AtomicLong(0L)
            val detector = mock<HotKeyDetector>()
            whenever(detector.isHot(any())).thenReturn(true)
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, hotKeyDetector = detector)
            val loads = AtomicInteger(0)

            now.set(0L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(100L); "v1" })
            now.set(600L)
            cache.get("k", Callable { loads.incrementAndGet(); now.addAndGet(50L); "v2" })

            assertThat(loads.get()).isEqualTo(2)
            assertThat(refreshSuccess()).isEqualTo(1.0)
        }

        @Test
        fun recordAccess는_L1_히트에서도_호출된다() {
            val now = AtomicLong(0L)
            val detector = mock<HotKeyDetector>()
            whenever(detector.isHot(any())).thenReturn(false)
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1.0 }, hotKeyDetector = detector)

            now.set(0L)
            cache.get("k", Callable { now.addAndGet(10L); "v1" }) // 콜드(미스)
            now.set(50L)
            cache.get("k", Callable { "v1" }) // L1 히트

            verify(detector, atLeast(2)).recordAccess(eq("k")) // 최상단 이동으로 L1 히트도 카운트
        }
    }

    @Nested
    @DisplayName("다듬기: 가드 패배 시 로컬 cooldown")
    inner class GuardLossCooldown {
        @Test
        fun 가드_패배시_cooldown_동안_tryAcquire를_반복호출하지_않는다() {
            val now = AtomicLong(0L)
            val guard = mock<RefreshGuard>()
            whenever(guard.tryAcquire(any(), any())).thenReturn(false)
            val cache = cache(hotPolicy, clock = { now.get() }, random = { 1e-6 }, refreshGuard = guard)

            now.set(0L)
            cache.get("k", Callable { now.addAndGet(100L); "v1" })

            now.set(600L)
            cache.get("k", Callable { "v2" }) // 트리거 → tryAcquire=false → cooldown(만료 1600) 설정
            now.set(700L) // cooldown 이내
            cache.get("k", Callable { "v3" }) // 억제됨 → tryAcquire 재호출 없음

            verify(guard, times(1)).tryAcquire(any(), any())
        }
    }
}
