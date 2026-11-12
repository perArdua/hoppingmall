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

@DisplayName("재고 예약 뒤 재고 캐시 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryReserveCacheMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    private var originalCaches: List<Cache>? = null

    @AfterEach
    fun restoreCaches() {
        originalCaches?.let { replaceCaches(it) }
        originalCaches = null
    }

    @Test
    fun 배치_예약_뒤_재고를_조회하면_줄어든_수량이_보인다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)
        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(10)
        assertThat(inventoryQueryService.getStock(PRODUCT_B).stockQuantity).isEqualTo(20)

        inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))

        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(7)
        assertThat(inventoryQueryService.getStock(PRODUCT_B).stockQuantity).isEqualTo(15)
    }

    @Test
    fun 배치_예약의_캐시_삭제는_예약이_커밋된_뒤에_실행된다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)
        val evictions = recordInventoryEvictions()

        inventoryCommandService.batchReserveStock(listOf(PRODUCT_B to 5, PRODUCT_A to 3))

        assertThat(evictions).containsExactlyInAnyOrder(Eviction(PRODUCT_A, 7), Eviction(PRODUCT_B, 15))
    }

    @Test
    fun 배치_예약이_재고_부족으로_실패하면_캐시를_지우지_않는다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 1)
        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(10)
        val evictions = recordInventoryEvictions()

        assertThatThrownBy { inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5)) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)

        assertThat(evictions).isEmpty()
        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(10)
    }

    @Test
    fun 단건_예약_뒤_재고를_조회하면_줄어든_수량이_보인다() {
        seed(PRODUCT_A, 10)
        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(10)

        inventoryCommandService.reserveStock(PRODUCT_A, 3)

        assertThat(inventoryQueryService.getStock(PRODUCT_A).stockQuantity).isEqualTo(7)
    }

    private fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

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
            jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!
        }.get(30, TimeUnit.SECONDS)

    private data class Eviction(val productId: Long, val stockSeenByOtherConnection: Int)

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
        private const val PRODUCT_A = 6001L
        private const val PRODUCT_B = 6002L
    }
}
