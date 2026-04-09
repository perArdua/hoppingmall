package com.hoppingmall.product.product.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("ProductDailyStatistics 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductDailyStatisticsTest {

    @Test
    fun 일별_통계를_생성한다() {
        val daily = ProductDailyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.of(2026, 1, 1),
            dailySalesQuantity = 10, dailySalesAmount = BigDecimal("100000"),
            dailyOrderCount = 5, endOfDayStock = 90
        )

        assertThat(daily.productId).isEqualTo(1L)
        assertThat(daily.statisticsDate).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(daily.dailySalesQuantity).isEqualTo(10)
        assertThat(daily.endOfDayStock).isEqualTo(90)
    }

    @Test
    fun 기본값으로_일별_통계를_생성한다() {
        val daily = ProductDailyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.of(2026, 1, 1)
        )

        assertThat(daily.dailySalesQuantity).isEqualTo(0)
        assertThat(daily.dailySalesAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun ProductStatistics로부터_일별_통계를_업데이트한다() {
        val daily = ProductDailyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.of(2026, 1, 1)
        )
        val stats = ProductStatistics.create(
            productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L,
            currentStock = 50
        )
        stats.incrementSales(10, BigDecimal("100000"))

        daily.updateFromStatistics(stats)

        assertThat(daily.dailySalesQuantity).isEqualTo(10)
        assertThat(daily.dailySalesAmount).isEqualByComparingTo(BigDecimal("100000"))
        assertThat(daily.dailyOrderCount).isEqualTo(1)
        assertThat(daily.endOfDayStock).isEqualTo(50)
    }
}
