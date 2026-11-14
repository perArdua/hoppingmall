package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.internal.InternalInventoryBucketController
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("기존 상품 버킷 채우기 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketBackfillMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var bucketBackfillService: BucketBackfillService

    @Autowired
    private lateinit var internalInventoryBucketController: InternalInventoryBucketController

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var inventoryBucketRepository: InventoryBucketRepository

    @Autowired
    private lateinit var inventoryCacheEvictor: InventoryCacheEvictor

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var queryService: InventoryQueryService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun 버킷이_없는_상품만_지금_재고로_버킷을_채운다() {
        inventory(FIRST, 10)
        inventory(SECOND, 99)
        transactionTemplate.execute { bucketStockService.initBuckets(SECOND, 40) }
        inventory(THIRD, 17)

        val result = bucketBackfillService.backfill()

        assertThat(result).isEqualTo(BucketBackfillService.BackfillResult(created = 2, failed = 0))
        assertThat(bucketStocks(FIRST)).containsExactly(3, 1, 1, 1, 1, 1, 1, 1)
        assertThat(bucketStocks(SECOND)).containsExactly(5, 5, 5, 5, 5, 5, 5, 5)
        assertThat(bucketStocks(THIRD)).containsExactly(3, 2, 2, 2, 2, 2, 2, 2)
    }

    @Test
    fun 다시_돌려도_이미_만든_버킷은_건드리지_않는다() {
        inventory(FIRST, 10)
        bucketBackfillService.backfill()
        jdbcTemplate.update("UPDATE inventory_buckets SET stock_quantity = 0 WHERE product_id = ? AND bucket_no = 0", FIRST)

        val result = bucketBackfillService.backfill()

        assertThat(result).isEqualTo(BucketBackfillService.BackfillResult(created = 0, failed = 0))
        assertThat(bucketStocks(FIRST)).containsExactly(0, 1, 1, 1, 1, 1, 1, 1)
    }

    @Test
    fun 배치_크기보다_상품이_많아도_상품_id_순서로_이어_모두_채운다() {
        val productIds = (1..5).map { FIRST + it * 10 }
        productIds.forEach { inventory(it, it.toInt() % 100) }
        val service = BucketBackfillService(inventoryBucketRepository, bucketStockService, inventoryCacheEvictor, transactionManager, 2)

        val result = service.backfill()

        assertThat(result.created).isEqualTo(5)
        productIds.forEach { assertThat(bucketStocks(it).sum()).isEqualTo(it.toInt() % 100) }
    }

    @Test
    fun 채운_뒤_재고_조회는_캐시가_비워져_지금_재고를_돌려준다() {
        inventory(FIRST, 10)
        assertThat(queryService.getStock(FIRST).stockQuantity).isZero()

        bucketBackfillService.backfill()

        assertThat(queryService.getStock(FIRST).stockQuantity).isEqualTo(10)
    }

    @Test
    fun 내부_입구로_채우면_만든_상품_수를_돌려준다() {
        inventory(FIRST, 10)

        val response = internalInventoryBucketController.backfill()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEqualTo(BucketBackfillService.BackfillResult(created = 1, failed = 0))
        assertThat(bucketStocks(FIRST).sum()).isEqualTo(10)
    }

    private fun inventory(productId: Long, stock: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stock))
    }

    private fun bucketStocks(productId: Long): List<Int> =
        jdbcTemplate.queryForList(
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? ORDER BY bucket_no",
            Int::class.java,
            productId
        )

    companion object {
        private const val FIRST = 9441L
        private const val SECOND = 9442L
        private const val THIRD = 9443L
    }
}
