package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
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
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("상품 일별 통계 마감 날짜 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class DailyStatisticsCloseDateMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val cartPort = object : CartItemQueryPort {
        override fun aggregateCartByProduct(): List<CartAggregation> = emptyList()
    }

    @BeforeEach
    fun setUp() {
        STATISTICS_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        val stats = ProductStatistics.create(productId = PRODUCT_ID, productName = "마감 상품", sellerId = 1L, categoryId = 1L)
        stats.incrementSales(10, BigDecimal("100000"))
        productStatisticsRepository.saveAndFlush(stats)
    }

    @AfterEach
    fun tearDown() {
        STATISTICS_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 자정에_도는_일별_마감은_방금_끝난_전날_날짜로_저장된다() {
        val before = LocalDate.now()

        scheduler().flushDailyStatistics()

        val after = LocalDate.now()
        assertThat(statisticsDate()).isIn(before.minusDays(1), after.minusDays(1))
        assertThat(dailySalesQuantity()).isEqualTo(10L)
    }

    private fun scheduler(): ProductStatisticsScheduler {
        val candidates = listOf(
            productStatisticsRepository, cartPort, inventoryRepository,
            productStatisticsCommandService, transactionTemplate, bucketStockService
        )
        val constructor = ProductStatisticsScheduler::class.java.constructors.single()
        val arguments = constructor.parameterTypes.map { type -> candidates.first { type.isInstance(it) } }
        return constructor.newInstance(*arguments.toTypedArray()) as ProductStatisticsScheduler
    }

    private fun dailySalesQuantity(): Long =
        jdbcTemplate.queryForObject(
            "SELECT daily_sales_quantity FROM product_daily_statistics WHERE product_id = ?", Long::class.java, PRODUCT_ID
        )!!

    private fun statisticsDate(): LocalDate =
        jdbcTemplate.queryForObject(
            "SELECT statistics_date FROM product_daily_statistics WHERE product_id = ?", LocalDate::class.java, PRODUCT_ID
        )!!

    companion object {
        private const val PRODUCT_ID = 19_002L
        private val STATISTICS_TABLES = listOf("product_daily_statistics", "product_statistics")
    }
}
