package com.hoppingmall.product.product.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("ProductStatistics 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductStatisticsTest {

    private fun createStats() = ProductStatistics.create(
        productId = 1L, productName = "테스트상품", sellerId = 1L, categoryId = 1L,
        totalSalesQuantity = 100, totalSalesAmount = BigDecimal("1000000"),
        currentStock = 50
    )

    @Test
    fun 통계를_생성한다() {
        val stats = createStats()

        assertThat(stats.productId).isEqualTo(1L)
        assertThat(stats.productName).isEqualTo("테스트상품")
        assertThat(stats.totalSalesQuantity).isEqualTo(100)
    }

    @Test
    fun 판매_통계를_증가시킨다() {
        val stats = createStats()

        stats.incrementSales(10, BigDecimal("100000"))

        assertThat(stats.totalSalesQuantity).isEqualTo(110)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo(BigDecimal("1100000"))
        assertThat(stats.todaySalesQuantity).isEqualTo(10)
        assertThat(stats.todayOrderCount).isEqualTo(1)
        assertThat(stats.orderCount).isEqualTo(1)
    }

    @Test
    fun 판매_통계를_감소시킨다() {
        val stats = createStats()
        stats.incrementSales(10, BigDecimal("100000"))

        stats.decrementSales(5, BigDecimal("50000"))

        assertThat(stats.totalSalesQuantity).isEqualTo(105)
        assertThat(stats.todaySalesQuantity).isEqualTo(5)
    }

    @Test
    fun 판매_통계_감소_시_0_미만으로_내려가지_않는다() {
        val stats = ProductStatistics.create(
            productId = 1L, productName = "테스트상품", sellerId = 1L, categoryId = 1L,
            totalSalesQuantity = 3, totalSalesAmount = BigDecimal("30000")
        )

        stats.decrementSales(10, BigDecimal("100000"))

        assertThat(stats.totalSalesQuantity).isEqualTo(0)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 환불_통계를_증가시킨다() {
        val stats = createStats()

        stats.incrementRefund(5, BigDecimal("50000"))

        assertThat(stats.totalRefundQuantity).isEqualTo(5)
        assertThat(stats.totalRefundAmount).isEqualByComparingTo(BigDecimal("50000"))
        assertThat(stats.todayRefundQuantity).isEqualTo(5)
    }

    @Test
    fun 오늘_통계를_초기화한다() {
        val stats = createStats()
        stats.incrementSales(10, BigDecimal("100000"))

        stats.resetToday()

        assertThat(stats.todaySalesQuantity).isEqualTo(0)
        assertThat(stats.todaySalesAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(stats.todayOrderCount).isEqualTo(0)
        assertThat(stats.todayRefundQuantity).isEqualTo(0)
        assertThat(stats.todayRefundAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 기간별_지표를_업데이트한다() {
        val stats = createStats()

        stats.updatePeriodMetrics(
            last7Days = BigDecimal("500000"),
            last30Days = BigDecimal("2000000"),
            previousWeekAmount = BigDecimal("400000")
        )

        assertThat(stats.last7DaysSalesAmount).isEqualByComparingTo(BigDecimal("500000"))
        assertThat(stats.last30DaysSalesAmount).isEqualByComparingTo(BigDecimal("2000000"))
        assertThat(stats.salesGrowthRate).isPositive()
    }

    @Test
    fun 이전_주_매출이_0일_때_성장률은_0이다() {
        val stats = createStats()

        stats.updatePeriodMetrics(
            last7Days = BigDecimal("500000"),
            last30Days = BigDecimal("2000000"),
            previousWeekAmount = BigDecimal.ZERO
        )

        assertThat(stats.salesGrowthRate).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 리뷰_통계를_업데이트한다() {
        val stats = createStats()

        stats.updateReviewStats(BigDecimal("4.50"), 100)

        assertThat(stats.averageRating).isEqualByComparingTo(BigDecimal("4.50"))
        assertThat(stats.reviewCount).isEqualTo(100)
    }

    @Test
    fun 장바구니_및_재고를_업데이트한다() {
        val stats = createStats()

        stats.updateCartAndInventory(30L, 200)

        assertThat(stats.currentCartCount).isEqualTo(30)
        assertThat(stats.currentStock).isEqualTo(200)
    }

    @Test
    fun update_메서드로_통계를_갱신한다() {
        val stats = createStats()

        stats.update(
            productName = "수정상품", categoryId = 2L,
            totalSalesQuantity = 200, totalSalesAmount = BigDecimal("2000000"),
            totalRefundQuantity = 10, totalRefundAmount = BigDecimal("100000"),
            currentCartCount = 50, currentStock = 100
        )

        assertThat(stats.productName).isEqualTo("수정상품")
        assertThat(stats.categoryId).isEqualTo(2L)
        assertThat(stats.totalSalesQuantity).isEqualTo(200)
        assertThat(stats.netRevenue).isEqualByComparingTo(BigDecimal("1900000"))
    }

    @Test
    fun 판매수량이_0이면_환불율은_0이다() {
        val stats = ProductStatistics.create(
            productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L,
            totalSalesQuantity = 0, totalSalesAmount = BigDecimal.ZERO
        )

        assertThat(stats.refundRate).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 재고가_0이면_재고회전율은_0이다() {
        val stats = ProductStatistics.create(
            productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L,
            totalSalesQuantity = 100, totalSalesAmount = BigDecimal("1000000"),
            currentStock = 0
        )

        assertThat(stats.stockTurnoverRate).isEqualByComparingTo(BigDecimal.ZERO)
    }
}
