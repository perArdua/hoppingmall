package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.port.CartAggregation
import com.hoppingmall.product.statistics.port.CartItemQueryPort
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate

abstract class ProductStatisticsStockSyncScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    protected lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val cartPort = object : CartItemQueryPort {
        override fun aggregateCartByProduct(): List<CartAggregation> = emptyList()
    }

    protected abstract fun seedStock(productId: Long, total: Int)

    @AfterEach
    fun deleteStatistics() {
        jdbcTemplate.update("DELETE FROM product_statistics WHERE product_id IN (?, ?, ?)", PRODUCT_A, PRODUCT_B, PRODUCT_C)
    }

    @Test
    fun 예약으로_줄어든_재고가_통계의_현재_재고에_반영된다() {
        seedStock(PRODUCT_A, 80)
        statistics(PRODUCT_A)
        inventoryCommandService.reserveStock(PRODUCT_A, 3)

        scheduler().syncCartAndInventory()

        assertThat(statisticsStock(PRODUCT_A)).isEqualTo(77)
        assertThat(statisticsStock(PRODUCT_A)).isEqualTo(inventoryQueryService.getStock(PRODUCT_A).stockQuantity)
    }

    @Test
    fun 여러_상품의_통계가_상품마다_자기_재고로_갱신된다() {
        seedStock(PRODUCT_A, 80)
        seedStock(PRODUCT_B, 40)
        seedStock(PRODUCT_C, 16)
        listOf(PRODUCT_A, PRODUCT_B, PRODUCT_C).forEach { statistics(it) }
        inventoryCommandService.reserveStock(PRODUCT_A, 3)
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_B to 5, PRODUCT_C to 2))
        inventoryCommandService.cancelReservation(reservations.getValue(PRODUCT_C))

        scheduler().syncCartAndInventory()

        assertThat(statisticsStock(PRODUCT_A)).isEqualTo(77)
        assertThat(statisticsStock(PRODUCT_B)).isEqualTo(35)
        assertThat(statisticsStock(PRODUCT_C)).isEqualTo(16)
    }

    @Test
    fun 재고_정보가_없는_상품의_통계_재고는_0이다() {
        statistics(PRODUCT_A)

        scheduler().syncCartAndInventory()

        assertThat(statisticsStock(PRODUCT_A)).isZero()
    }

    private fun scheduler(): ProductStatisticsScheduler {
        val candidates = listOf(
            productStatisticsRepository, cartPort, inventoryRepository,
            mock<ProductStatisticsCommandService>(), transactionTemplate, bucketStockService
        )
        val constructor = ProductStatisticsScheduler::class.java.constructors.single()
        val arguments = constructor.parameterTypes.map { type -> candidates.first { type.isInstance(it) } }
        return constructor.newInstance(*arguments.toTypedArray()) as ProductStatisticsScheduler
    }

    private fun statistics(productId: Long) {
        productStatisticsRepository.saveAndFlush(
            ProductStatistics.create(productId = productId, productName = "상품-$productId", sellerId = 1L, categoryId = 1L)
        )
    }

    private fun statisticsStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT current_stock FROM product_statistics WHERE product_id = ?", Int::class.java, productId)!!

    companion object {
        const val PRODUCT_A = 18_001L
        const val PRODUCT_B = 18_002L
        const val PRODUCT_C = 18_003L
    }
}

@DisplayName("single 모드 통계 재고 동기화 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleProductStatisticsStockSyncMySqlIntegrationTest : ProductStatisticsStockSyncScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }
}

@DisplayName("bucket 모드 통계 재고 동기화 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketProductStatisticsStockSyncMySqlIntegrationTest : ProductStatisticsStockSyncScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, total / 8
            )
        }
    }
}
