package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.test.context.TestPropertySource
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

abstract class InventoryCancelCacheScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    protected lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    private var originalCaches: List<Cache>? = null

    protected abstract fun seedStock(productId: Long, total: Int)

    protected abstract fun committedStockSql(): String

    @AfterEach
    fun restoreCaches() {
        originalCaches?.let { replaceCaches(it) }
        originalCaches = null
    }

    @Test
    fun 단건_취소_뒤_재고를_조회하면_복원된_수량이_보인다() {
        seedStock(PRODUCT_A, 80)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(77)

        inventoryCommandService.cancelReservation(reservationId)

        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
    }

    @Test
    fun 배치_취소_뒤_재고를_조회하면_두_상품_모두_복원된_수량이_보인다() {
        seedStock(PRODUCT_A, 80)
        seedStock(PRODUCT_B, 40)
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(77)
        assertThat(stockSeenByQuery(PRODUCT_B)).isEqualTo(35)

        inventoryCommandService.cancelReservations(reservations.values.toList())

        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
        assertThat(stockSeenByQuery(PRODUCT_B)).isEqualTo(40)
    }

    @Test
    fun 취소의_캐시_삭제는_복원이_커밋된_뒤에_실행된다() {
        seedStock(PRODUCT_A, 80)
        seedStock(PRODUCT_B, 40)
        val single = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        val batch = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 2, PRODUCT_B to 5))
        val evictions = recordInventoryEvictions()

        inventoryCommandService.cancelReservation(single)

        assertThat(evictions).containsExactly(Eviction(PRODUCT_A, 78))

        inventoryCommandService.cancelReservations(batch.values.toList())

        assertThat(evictions.drop(1)).containsExactlyInAnyOrder(Eviction(PRODUCT_A, 80), Eviction(PRODUCT_B, 40))
    }

    @Test
    fun 취소할_것이_없는_호출은_캐시를_지우지_않는다() {
        seedStock(PRODUCT_A, 80)
        val cancelled = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        inventoryCommandService.cancelReservation(cancelled)
        val confirmed = inventoryCommandService.reserveStock(PRODUCT_A, 2)
        inventoryCommandService.confirmReservations(listOf(confirmed))
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(78)
        val evictions = recordInventoryEvictions()

        inventoryCommandService.cancelReservation(cancelled)
        inventoryCommandService.cancelReservation("rsv-unknown")
        inventoryCommandService.cancelReservation(confirmed)
        inventoryCommandService.cancelReservations(listOf(cancelled, "rsv-unknown", confirmed))

        assertThat(evictions).isEmpty()
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(78)
    }

    @Test
    fun 배치_예약이_재고_부족으로_실패하면_보상_취소가_돌아도_캐시를_지우지_않는다() {
        seedStock(PRODUCT_A, 80)
        seedStock(PRODUCT_B, 40)
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
        val evictions = recordInventoryEvictions()

        assertThatThrownBy { inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 41)) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)

        assertThat(evictions).isEmpty()
        assertThat(stockSeenByQuery(PRODUCT_A)).isEqualTo(80)
    }

    private fun stockSeenByQuery(productId: Long): Int = inventoryQueryService.getStock(productId).stockQuantity

    private fun recordInventoryEvictions(): List<Eviction> {
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
        const val PRODUCT_A = 17_001L
        const val PRODUCT_B = 17_002L
    }
}

@DisplayName("single 모드 취소 뒤 재고 캐시 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleInventoryCancelCacheMySqlIntegrationTest : InventoryCancelCacheScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }

    override fun committedStockSql(): String = "SELECT stock_quantity FROM inventories WHERE product_id = ?"
}

@DisplayName("bucket 모드 취소 뒤 재고 캐시 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketInventoryCancelCacheMySqlIntegrationTest : InventoryCancelCacheScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, total / 8
            )
        }
    }

    override fun committedStockSql(): String = "SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?"
}
