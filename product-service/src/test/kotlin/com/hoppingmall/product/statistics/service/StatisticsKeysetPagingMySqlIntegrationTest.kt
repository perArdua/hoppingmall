package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandServiceImpl
import com.hoppingmall.product.statistics.port.CartAggregation
import com.hoppingmall.product.statistics.port.CartItemQueryPort
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("통계 마감·동기화 잡 키 기준 페이지 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class StatisticsKeysetPagingMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var productDailyStatisticsRepository: ProductDailyStatisticsRepository

    @Autowired
    private lateinit var productHourlyStatisticsRepository: ProductHourlyStatisticsRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanStatistics() {
        deleteStatistics()
    }

    @AfterEach
    fun cleanStatisticsAfter() {
        deleteStatistics()
    }

    @Test
    fun 시간별_스냅샷_도중_앞쪽_행이_오늘_활동에서_빠져도_활동_상품을_하나도_건너뛰지_않는다() {
        val ids = seedStatistics(150)
        jdbcTemplate.update("UPDATE product_statistics SET today_sales_quantity = 1")
        val service = commandService(afterFirstChunk = {
            jdbcTemplate.update(
                "UPDATE product_statistics SET today_sales_quantity = 0, today_refund_quantity = 0 ORDER BY id LIMIT 10"
            )
        })

        service.flushHourlySnapshot()

        assertThat(productIds("product_hourly_statistics")).containsExactlyInAnyOrderElementsOf(ids)
    }

    @Test
    fun 일별_마감_도중_앞쪽_행이_지워져도_남은_상품을_하나도_건너뛰지_않는다() {
        val ids = seedStatistics(150)
        val service = commandService(afterFirstChunk = {
            jdbcTemplate.update("DELETE FROM product_statistics ORDER BY id LIMIT 10")
        })

        service.flushDailySnapshot()

        assertThat(productIds("product_daily_statistics")).containsExactlyInAnyOrderElementsOf(ids)
    }

    @Test
    fun 장바구니_재고_동기화_도중_앞쪽_행이_지워져도_남은_상품을_하나도_건너뛰지_않는다() {
        val ids = seedStatistics(600)
        val cartPort = object : CartItemQueryPort {
            override fun aggregateCartByProduct() = ids.map { CartAggregation(productId = it, buyerCount = 3L) }
        }
        val transactionTemplate = hookedTemplate {
            jdbcTemplate.update("DELETE FROM product_statistics ORDER BY id LIMIT 10")
        }
        val scheduler = ProductStatisticsScheduler(
            productStatisticsRepository,
            cartPort,
            inventoryRepository,
            bucketStockService,
            commandService(afterFirstChunk = {}),
            transactionTemplate
        )

        scheduler.syncCartAndInventory()

        val notSynced = jdbcTemplate.queryForList(
            "SELECT product_id FROM product_statistics WHERE current_cart_count <> 3",
            Long::class.java
        )
        assertThat(notSynced).isEmpty()
    }

    private fun commandService(afterFirstChunk: () -> Unit) = ProductStatisticsCommandServiceImpl(
        productStatisticsRepository,
        productDailyStatisticsRepository,
        productHourlyStatisticsRepository,
        productRepository,
        hookedTemplate(afterFirstChunk)
    )

    private fun hookedTemplate(afterFirstChunk: () -> Unit): TransactionTemplate =
        object : TransactionTemplate(transactionManager) {
            private var chunks = 0

            override fun <T> execute(action: TransactionCallback<T>): T? {
                val result = super.execute(action)
                chunks++
                if (chunks == 1) afterFirstChunk()
                return result
            }
        }

    private fun seedStatistics(count: Int): List<Long> {
        val saved = productStatisticsRepository.saveAllAndFlush(
            (1..count).map {
                ProductStatistics.create(
                    productId = BASE_PRODUCT_ID + it,
                    productName = "페이지 상품 $it",
                    sellerId = 1L,
                    categoryId = 1L
                )
            }
        )
        return saved.map { it.productId }
    }

    private fun productIds(table: String): List<Long> =
        jdbcTemplate.queryForList("SELECT product_id FROM $table", Long::class.java)

    private fun deleteStatistics() {
        jdbcTemplate.update("DELETE FROM product_hourly_statistics")
        jdbcTemplate.update("DELETE FROM product_daily_statistics")
        jdbcTemplate.update("DELETE FROM product_statistics")
    }

    companion object {
        private const val BASE_PRODUCT_ID = 880000L
    }
}
