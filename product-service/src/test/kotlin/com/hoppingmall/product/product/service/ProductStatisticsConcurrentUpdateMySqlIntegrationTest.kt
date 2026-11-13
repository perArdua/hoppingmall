package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@DisplayName("상품 통계 동시 갱신 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductStatisticsConcurrentUpdateMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @BeforeEach
    @AfterEach
    fun cleanStatistics() {
        jdbcTemplate.update("DELETE FROM product_statistics WHERE product_id = ?", PRODUCT_ID)
    }

    @Test
    fun 같은_상품의_판매_가산_두_건이_동시에_오면_둘_다_반영한다() {
        seed()

        runConcurrentlyWhileRowLocked(
            { productStatisticsCommandService.incrementSalesStats(PRODUCT_ID, 2, BigDecimal("20000")) },
            { productStatisticsCommandService.incrementSalesStats(PRODUCT_ID, 3, BigDecimal("30000")) }
        )

        val stats = stats()
        assertThat(stats.totalSalesQuantity).isEqualTo(15L)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("150000")
        assertThat(stats.todaySalesQuantity).isEqualTo(5L)
        assertThat(stats.todaySalesAmount).isEqualByComparingTo("50000")
        assertThat(stats.orderCount).isEqualTo(3L)
        assertThat(stats.todayOrderCount).isEqualTo(2L)
        assertThat(stats.netRevenue).isEqualByComparingTo("150000")
        assertThat(stats.averageOrderAmount).isEqualByComparingTo("50000")
    }

    @Test
    fun 같은_상품의_판매_감산_두_건이_동시에_오면_둘_다_반영한다() {
        seed()

        runConcurrentlyWhileRowLocked(
            { productStatisticsCommandService.decrementSalesStats(PRODUCT_ID, 2, BigDecimal("20000")) },
            { productStatisticsCommandService.decrementSalesStats(PRODUCT_ID, 3, BigDecimal("30000")) }
        )

        val stats = stats()
        assertThat(stats.totalSalesQuantity).isEqualTo(5L)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("50000")
        assertThat(stats.orderCount).isEqualTo(0L)
        assertThat(stats.netRevenue).isEqualByComparingTo("50000")
    }

    @Test
    fun 같은_상품의_환불_가산_두_건이_동시에_오면_둘_다_반영한다() {
        seed()

        runConcurrentlyWhileRowLocked(
            { productStatisticsCommandService.incrementRefundStats(PRODUCT_ID, 1, BigDecimal("10000")) },
            { productStatisticsCommandService.incrementRefundStats(PRODUCT_ID, 4, BigDecimal("40000")) }
        )

        val stats = stats()
        assertThat(stats.totalRefundQuantity).isEqualTo(5L)
        assertThat(stats.totalRefundAmount).isEqualByComparingTo("50000")
        assertThat(stats.todayRefundQuantity).isEqualTo(5L)
        assertThat(stats.refundRate).isEqualByComparingTo("0.5")
        assertThat(stats.netRevenue).isEqualByComparingTo("50000")
    }

    @Test
    fun 감산은_0_아래로_내려가지_않는다() {
        seed()

        productStatisticsCommandService.decrementSalesStats(PRODUCT_ID, 30, BigDecimal("300000"))

        val stats = stats()
        assertThat(stats.totalSalesQuantity).isZero()
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("0")
        assertThat(stats.orderCount).isZero()
    }

    private fun seed() {
        productStatisticsRepository.saveAndFlush(
            ProductStatistics.create(
                productId = PRODUCT_ID,
                productName = "동시 갱신 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 10,
                totalSalesAmount = BigDecimal("100000")
            )
        )
        jdbcTemplate.update("UPDATE product_statistics SET order_count = 1 WHERE product_id = ?", PRODUCT_ID)
    }

    private fun stats(): ProductStatistics = productStatisticsRepository.findByProductId(PRODUCT_ID)!!

    private fun runConcurrentlyWhileRowLocked(vararg actions: () -> Unit) {
        val results = dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                holder.prepareStatement("SELECT id FROM product_statistics WHERE product_id = ? FOR UPDATE").use {
                    it.setLong(1, PRODUCT_ID)
                    it.executeQuery().close()
                }
                val pending = actions.map { action -> executor.submit<Result<Unit>> { runCatching { action() } } }
                awaitLockWaits(actions.size.toLong())
                holder.commit()
                pending.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }
        results.forEach { assertThat(it.isSuccess).withFailMessage { it.exceptionOrNull().toString() }.isTrue() }
    }

    private fun awaitLockWaits(expected: Long) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(TABLE) >= expected) return
            Thread.onSpinWait()
        }
        throw AssertionError("$TABLE 잠금 대기 ${expected}건이 ${LOCK_WAIT_LIMIT_MS}ms 안에 보이지 않았다: ${lockWaitCount(TABLE)}건")
    }

    companion object {
        private const val PRODUCT_ID = 7201L
        private const val TABLE = "product_statistics"
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
    }
}
