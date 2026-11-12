package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@DisplayName("재고 예약의 자기 트랜잭션 실행 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryReserveOwnTransactionMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var cacheManager: CacheManager

    private var originalCaches: List<Cache>? = null

    @AfterEach
    fun restoreCaches() {
        originalCaches?.let { replaceCaches(it) }
        originalCaches = null
    }

    @Test
    fun 호출자_트랜잭션_안에서_단건_예약한_뒤_호출자가_롤백해도_예약은_남는다() {
        seed(PRODUCT_A, 10)

        callerTransaction().executeWithoutResult { status ->
            inventoryCommandService.reserveStock(PRODUCT_A, 3)
            status.setRollbackOnly()
        }

        assertThat(stock(PRODUCT_A)).isEqualTo(7)
        assertThat(reservationStatuses(PRODUCT_A)).containsExactly("RESERVED")
    }

    @Test
    fun 호출자_트랜잭션_안에서_배치_예약한_뒤_호출자가_롤백해도_예약은_남는다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)

        callerTransaction().executeWithoutResult { status ->
            inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))
            status.setRollbackOnly()
        }

        assertThat(stock(PRODUCT_A)).isEqualTo(7)
        assertThat(stock(PRODUCT_B)).isEqualTo(15)
        assertThat(reservationStatuses(PRODUCT_A)).containsExactly("RESERVED")
        assertThat(reservationStatuses(PRODUCT_B)).containsExactly("RESERVED")
    }

    @Test
    fun 호출자_트랜잭션_안에서_단건_예약한_뒤_호출자가_커밋하면_예약이_남는다() {
        seed(PRODUCT_A, 10)

        callerTransaction().executeWithoutResult {
            inventoryCommandService.reserveStock(PRODUCT_A, 3)
        }

        assertThat(stock(PRODUCT_A)).isEqualTo(7)
        assertThat(reservationStatuses(PRODUCT_A)).containsExactly("RESERVED")
    }

    @Test
    fun 호출자_트랜잭션_안에서_배치_예약한_뒤_호출자가_커밋하면_예약이_남는다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)

        callerTransaction().executeWithoutResult {
            inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))
        }

        assertThat(stock(PRODUCT_A)).isEqualTo(7)
        assertThat(stock(PRODUCT_B)).isEqualTo(15)
        assertThat(reservationStatuses(PRODUCT_A)).containsExactly("RESERVED")
        assertThat(reservationStatuses(PRODUCT_B)).containsExactly("RESERVED")
    }

    @Test
    fun 단건_예약의_캐시_삭제는_예약이_커밋된_뒤에_실행된다() {
        seed(PRODUCT_A, 10)
        val evictions = recordInventoryEvictions()

        inventoryCommandService.reserveStock(PRODUCT_A, 3)

        assertThat(evictions).containsExactly(Eviction(PRODUCT_A, 7))
    }

    @Test
    fun 호출자_트랜잭션_안에서_부른_단건_예약의_캐시_삭제도_예약이_커밋된_뒤에_실행된다() {
        seed(PRODUCT_A, 10)
        val evictions = recordInventoryEvictions()

        callerTransaction().executeWithoutResult {
            inventoryCommandService.reserveStock(PRODUCT_A, 3)
        }

        assertThat(evictions).containsExactly(Eviction(PRODUCT_A, 7))
    }

    @Test
    fun 호출자_트랜잭션_안에서_부른_배치_예약의_캐시_삭제도_예약이_커밋된_뒤에_실행된다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)
        val evictions = recordInventoryEvictions()

        callerTransaction().executeWithoutResult {
            inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5))
        }

        assertThat(evictions).containsExactlyInAnyOrder(Eviction(PRODUCT_A, 7), Eviction(PRODUCT_B, 15))
    }

    private fun callerTransaction(): TransactionTemplate = TransactionTemplate(transactionManager)

    private fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

    private fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    private fun reservationStatuses(productId: Long): List<String> =
        jdbcTemplate.queryForList("SELECT status FROM inventory_reservations WHERE product_id = ?", String::class.java, productId)

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
        executor.submit<Int> { stock(productId) }.get(30, TimeUnit.SECONDS)

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
        private const val PRODUCT_A = 6101L
        private const val PRODUCT_B = 6102L
    }
}
