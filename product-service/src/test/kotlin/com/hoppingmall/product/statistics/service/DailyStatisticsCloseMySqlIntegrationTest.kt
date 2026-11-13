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

@DisplayName("상품 일별 통계 마감 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class DailyStatisticsCloseMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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
    fun 일별_마감이_두_번_돌아도_그날_통계가_0으로_덮이지_않는다() {
        scheduler().flushDailyStatistics()
        scheduler().flushDailyStatistics()

        assertThat(dailyRowCount()).isEqualTo(1)
        assertThat(dailySalesQuantity()).isEqualTo(10L)
        assertThat(dailySalesAmount()).isEqualByComparingTo("100000")
        assertThat(todaySalesQuantity()).isZero()
    }

    @Test
    fun 다른_인스턴스가_마감하는_중이면_끝날_때까지_기다린_뒤_이미_마감된_통계를_덮지_않는다() {
        val closedDate = LocalDate.now().minusDays(1)

        val result = runWhileHolding(
            holdingChanges = { holder ->
                holder.prepareStatement(
                    """
                    INSERT INTO product_daily_statistics
                        (product_id, statistics_date, daily_sales_quantity, daily_sales_amount, daily_order_count,
                         daily_refund_quantity, daily_refund_amount, end_of_day_stock, created_at)
                    VALUES (?, ?, 10, 100000, 1, 0, 0, 0, NOW())
                    """.trimIndent()
                ).use {
                    it.setLong(1, PRODUCT_ID)
                    it.setObject(2, closedDate)
                    it.executeUpdate()
                }
                holder.prepareStatement(
                    "UPDATE product_statistics SET today_sales_quantity = 0, today_sales_amount = 0, today_order_count = 0 WHERE product_id = ?"
                ).use {
                    it.setLong(1, PRODUCT_ID)
                    it.executeUpdate()
                }
            },
            actionName = "두 번째 인스턴스의 일별 마감",
            waitTable = "product_statistics",
            action = { scheduler().flushDailyStatistics() }
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(dailyRowCount()).isEqualTo(1)
        assertThat(dailySalesQuantity()).isEqualTo(10L)
        assertThat(todaySalesQuantity()).isZero()
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

    private fun dailyRowCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_daily_statistics WHERE product_id = ?", Int::class.java, PRODUCT_ID)!!

    private fun dailySalesQuantity(): Long =
        jdbcTemplate.queryForObject(
            "SELECT daily_sales_quantity FROM product_daily_statistics WHERE product_id = ?", Long::class.java, PRODUCT_ID
        )!!

    private fun dailySalesAmount(): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT daily_sales_amount FROM product_daily_statistics WHERE product_id = ?", BigDecimal::class.java, PRODUCT_ID
        )!!

    private fun todaySalesQuantity(): Long =
        jdbcTemplate.queryForObject(
            "SELECT today_sales_quantity FROM product_statistics WHERE product_id = ?", Long::class.java, PRODUCT_ID
        )!!

    companion object {
        private const val PRODUCT_ID = 19_001L
        private val STATISTICS_TABLES = listOf("product_daily_statistics", "product_statistics")
    }
}
