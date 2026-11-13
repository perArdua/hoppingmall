package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.Inventory
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
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("장바구니·재고 집계 스케줄러 동시 갱신 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductStatisticsSchedulerConcurrentUpdateMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val cartPort = object : CartItemQueryPort {
        override fun aggregateCartByProduct(): List<CartAggregation> = listOf(CartAggregation(PRODUCT_ID, 4L))
    }

    @BeforeEach
    @AfterEach
    fun cleanStatistics() {
        jdbcTemplate.update("DELETE FROM product_statistics WHERE product_id = ?", PRODUCT_ID)
    }

    @Test
    fun 집계가_읽은_뒤_커밋된_판매_가산을_덮어쓰지_않는다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 30))
        productStatisticsRepository.saveAndFlush(
            ProductStatistics.create(
                productId = PRODUCT_ID,
                productName = "집계 동시 갱신 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 10,
                totalSalesAmount = BigDecimal("100000")
            )
        )

        val result = runWhileHolding(
            holdingChanges = { holder ->
                holder.prepareStatement(
                    """
                    UPDATE product_statistics
                    SET total_sales_quantity = total_sales_quantity + 5,
                        total_sales_amount = total_sales_amount + 50000,
                        order_count = order_count + 1
                    WHERE product_id = ?
                    """.trimIndent()
                ).use {
                    it.setLong(1, PRODUCT_ID)
                    it.executeUpdate()
                }
            },
            actionName = "장바구니·재고 집계",
            waitTable = "product_statistics",
            action = { scheduler().syncCartAndInventory() }
        )

        assertThat(result.isSuccess).withFailMessage { result.exceptionOrNull().toString() }.isTrue()
        val row = jdbcTemplate.queryForMap(
            """
            SELECT total_sales_quantity, total_sales_amount, order_count, current_cart_count, current_stock, stock_turnover_rate
            FROM product_statistics WHERE product_id = ?
            """.trimIndent(),
            PRODUCT_ID
        )
        assertThat((row["total_sales_quantity"] as Number).toLong()).isEqualTo(15L)
        assertThat(row["total_sales_amount"] as BigDecimal).isEqualByComparingTo("150000")
        assertThat((row["order_count"] as Number).toLong()).isEqualTo(1L)
        assertThat((row["current_cart_count"] as Number).toLong()).isEqualTo(4L)
        assertThat((row["current_stock"] as Number).toInt()).isEqualTo(30)
        assertThat(row["stock_turnover_rate"] as BigDecimal).isEqualByComparingTo("0.5")
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

    companion object {
        private const val PRODUCT_ID = 7301L
    }
}
