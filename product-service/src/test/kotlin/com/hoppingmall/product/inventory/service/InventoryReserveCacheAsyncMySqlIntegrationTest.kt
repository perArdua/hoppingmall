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
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@DisplayName("재고 예약 뒤 재고 캐시 MySQL 통합 테스트 (기본값, 비동기 삭제)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryReserveCacheAsyncMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var asyncEvictor: InventoryCacheAsyncEvictor

    private var originalCaches: List<Cache>? = null

    @AfterEach
    fun restoreCaches() {
        awaitUntil("비동기 삭제 대기열이 비지 않았다") { asyncEvictor.queueSize() == 0 }
        originalCaches?.let { replaceCaches(it) }
        originalCaches = null
    }

    @Test
    fun 배치_예약_뒤_재고를_조회하면_곧_줄어든_수량이_보인다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)
        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(10)
        assertThat(inventoryQueryService.getStock(PRODUCT_B).stockQuantity).isEqualTo(20)

        inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))

        awaitUntil("줄어든 재고가 조회되지 않았다") {
            inventoryQueryService.getStock(PRODUCT_A).stockQuantity == 7 &&
                inventoryQueryService.getStock(PRODUCT_B).stockQuantity == 15
        }
    }

    @Test
    fun 배치_예약의_캐시_삭제는_커밋된_뒤_비동기_실행기에서_실행된다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)
        val evictions = recordInventoryEvictions()

        inventoryCommandService.batchReserveStock(listOf(PRODUCT_B to 5, PRODUCT_A to 3))

        awaitUntil("캐시 삭제가 두 번 실행되지 않았다") { evictions.size == 2 }
        assertThat(evictions).containsExactlyInAnyOrder(
            Eviction(PRODUCT_A, 7, EVICT_THREAD),
            Eviction(PRODUCT_B, 15, EVICT_THREAD)
        )
    }

    @Test
    fun 배치_예약이_재고_부족으로_실패하면_캐시_삭제를_넘기지_않는다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 1)
        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(10)
        val evictions = recordInventoryEvictions()

        assertThatThrownBy { inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5)) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)

        assertThat(asyncEvictor.queueSize()).isZero()
        assertThat(evictions).isEmpty()
        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(10)
    }

    private fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

    private fun awaitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_LIMIT_MS
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("${AWAIT_LIMIT_MS}ms 안에 $message")
            Thread.sleep(10)
        }
    }

    private fun recordInventoryEvictions(): List<Eviction> {
        val evictions = Collections.synchronizedList(mutableListOf<Eviction>())
        val manager = cacheManager as SimpleCacheManager
        val caches = manager.cacheNames.map { manager.getCache(it)!! }
        originalCaches = caches
        replaceCaches(
            caches.map { cache ->
                if (cache.name == INVENTORY_CACHE) {
                    EvictionRecordingCache(cache) { key -> evictions.add(Eviction(key, committedStock(key), Thread.currentThread().name)) }
                } else {
                    cache
                }
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
            jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!
        }.get(30, TimeUnit.SECONDS)

    private data class Eviction(val productId: Long, val stockSeenByOtherConnection: Int, val thread: String)

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
        private const val EVICT_THREAD = "inventory-cache-evict"
        private const val AWAIT_LIMIT_MS = 5_000L
        private const val PRODUCT_A = 6101L
        private const val PRODUCT_B = 6102L
    }
}
