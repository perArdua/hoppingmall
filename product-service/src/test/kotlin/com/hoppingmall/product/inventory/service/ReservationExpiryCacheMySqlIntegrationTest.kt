package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

abstract class ReservationExpiryCacheSupport : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    protected lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Autowired
    protected lateinit var inventoryReservationRepository: InventoryReservationRepository

    @Autowired
    protected lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    protected lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var cacheManager: CacheManager

    private var originalCaches: List<Cache>? = null

    protected abstract fun committedStockSql(): String

    @AfterEach
    fun restoreCaches() {
        originalCaches?.let { replaceCaches(it) }
        originalCaches = null
    }

    protected fun <T : Any> wired(job: T): T = job.also { applicationContext.autowireCapableBeanFactory.autowireBean(it) }

    protected fun stockSeenByQuery(productId: Long): Int = inventoryQueryService.getStock(productId).stockQuantity

    protected fun makeExpired(vararg reservationIds: String) {
        reservationIds.forEach {
            jdbcTemplate.update(
                "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
                LocalDateTime.now().minusMinutes(1), it
            )
        }
    }

    protected fun makeCreatedBefore(reservationId: String, seconds: Long) {
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET created_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusSeconds(seconds), reservationId
        )
    }

    protected fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    protected fun recordInventoryEvictions(): List<Eviction> {
        val evictions = Collections.synchronizedList(mutableListOf<Eviction>())
        val manager = cacheManager as SimpleCacheManager
        val caches = manager.cacheNames.map { manager.getCache(it)!! }
        originalCaches = caches
        replaceCaches(
            caches.map { cache ->
                if (cache.name == INVENTORY_CACHE) EvictionRecordingCache(cache) { key -> evictions.add(Eviction(key, committedStock(key))) } else cache
            }
        )
        return evictions
    }

    private fun replaceCaches(caches: List<Cache>) {
        val manager = cacheManager as SimpleCacheManager
        manager.setCaches(caches)
        manager.initializeCaches()
    }

    private fun committedStock(productId: Long): Int =
        executor.submit<Int> {
            jdbcTemplate.queryForObject(committedStockSql(), Int::class.java, productId)!!
        }.get(30, TimeUnit.SECONDS)

    protected data class Eviction(val productId: Long, val stockSeenByOtherConnection: Int)

    private class EvictionRecordingCache(private val delegate: Cache, private val onEvict: (Long) -> Unit) : Cache {
        override fun getName(): String = delegate.name
        override fun getNativeCache(): Any = delegate.nativeCache
        override fun get(key: Any): Cache.ValueWrapper? = delegate.get(key)
        override fun <T : Any?> get(key: Any, type: Class<T>?): T? = delegate.get(key, type)
        override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? = delegate.get(key, valueLoader)
        override fun put(key: Any, value: Any?) = delegate.put(key, value)
        override fun clear() = delegate.clear()
        override fun evict(key: Any) {
            onEvict(key as Long)
            delegate.evict(key)
        }
    }

    companion object {
        private const val INVENTORY_CACHE = "inventory"
        const val PRODUCT_A = 19_001L
        const val PRODUCT_B = 19_002L
    }
}

@DisplayName("single 모드 예약 만료 뒤 재고 캐시 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleReservationExpiryCacheMySqlIntegrationTest : ReservationExpiryCacheSupport() {

    @Test
    fun 만료_스케줄러가_재고를_되돌린_뒤_재고를_조회하면_되돌아온_수량이_보인다() {
        seedStock(PRODUCT_A, 80)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(77)
        makeExpired(reservationId)

        transactionTemplate.execute { scheduler().expireReservations() }

        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
    }

    @Test
    fun 만료_스케줄러의_캐시_삭제는_복원이_커밋된_뒤에_실행된다() {
        seedStock(PRODUCT_A, 80)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        makeExpired(reservationId)
        val evictions = recordInventoryEvictions()

        transactionTemplate.execute { scheduler().expireReservations() }

        assertThat(evictions).containsExactly(Eviction(PRODUCT_A, 80))
    }

    @Test
    fun 만료_스케줄러의_캐시_삭제는_상품마다_복원이_커밋된_뒤에_실행된다() {
        seedStock(PRODUCT_A, 80)
        seedStock(PRODUCT_B, 40)
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))
        makeExpired(*reservations.values.toTypedArray())
        val evictions = recordInventoryEvictions()

        transactionTemplate.execute { scheduler().expireReservations() }

        assertThat(evictions).containsExactlyInAnyOrder(Eviction(PRODUCT_A, 80), Eviction(PRODUCT_B, 40))
    }

    private fun scheduler() = wired(ReservationExpiryScheduler(inventoryReservationRepository, inventoryRepository))

    private fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }

    override fun committedStockSql(): String = "SELECT stock_quantity FROM inventories WHERE product_id = ?"
}

@DisplayName("bucket 모드 예약 만료 뒤 재고 캐시 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketReservationExpiryCacheMySqlIntegrationTest : ReservationExpiryCacheSupport() {

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    private val abortingPort = object : ReservationLinkPort {
        override fun check(reservationIds: List<String>): ReservationLinkPort.LinkResult =
            ReservationLinkPort.LinkResult(emptyList(), reservationIds)
    }

    @Test
    fun 버킷_만료_잡이_재고를_되돌린_뒤_재고를_조회하면_되돌아온_수량이_보인다() {
        seedStock(PRODUCT_A, 80)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(77)
        makeExpired(reservationId)

        expiryJob().expireReservations()

        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
    }

    @Test
    fun 버킷_만료_잡의_캐시_삭제는_상품마다_복원이_커밋된_뒤에_실행된다() {
        seedStock(PRODUCT_A, 80)
        seedStock(PRODUCT_B, 40)
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))
        makeExpired(*reservations.values.toTypedArray())
        val evictions = recordInventoryEvictions()

        expiryJob().expireReservations()

        assertThat(evictions).containsExactlyInAnyOrder(Eviction(PRODUCT_A, 80), Eviction(PRODUCT_B, 40))
    }

    @Test
    fun 링크_확인_잡이_연결되지_않은_예약을_만료한_뒤_재고를_조회하면_되돌아온_수량이_보인다() {
        seedStock(PRODUCT_A, 80)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(77)
        makeExpired(reservationId)

        val result = linkCheckJob().runCycle(LocalDateTime.now())

        assertThat(result.aborted).isEqualTo(1)
        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
    }

    @Test
    fun 링크_확인_잡이_생성된_지_바닥_시간이_지난_예약을_만료한_뒤_재고를_조회하면_되돌아온_수량이_보인다() {
        seedStock(PRODUCT_A, 80)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(77)
        makeCreatedBefore(reservationId, FLOOR_SECONDS + 1)

        val result = linkCheckJob().runCycle(LocalDateTime.now())

        assertThat(result.floor).isEqualTo(1)
        assertThat(result.aborted).isZero()
        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
    }

    @Test
    fun 링크_확인_잡의_캐시_삭제는_상품마다_복원이_커밋된_뒤에_실행된다() {
        seedStock(PRODUCT_A, 80)
        seedStock(PRODUCT_B, 40)
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))
        makeExpired(*reservations.values.toTypedArray())
        val evictions = recordInventoryEvictions()

        linkCheckJob().runCycle(LocalDateTime.now())

        assertThat(evictions).containsExactlyInAnyOrder(Eviction(PRODUCT_A, 80), Eviction(PRODUCT_B, 40))
    }

    private fun expiryJob() = wired(
        BucketReservationExpiryJob(inventoryReservationRepository, bucketStockService, transactionManager, SimpleMeterRegistry(), true, 1000)
    )

    private fun linkCheckJob() = wired(
        ReservationLinkCheckJob(
            inventoryReservationRepository, bucketStockService, abortingPort, transactionManager, SimpleMeterRegistry(),
            500, 2500, FLOOR_SECONDS, 24
        )
    )

    private fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, total / 8
            )
        }
    }

    override fun committedStockSql(): String = "SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?"

    companion object {
        private const val FLOOR_SECONDS = 600L
    }
}
