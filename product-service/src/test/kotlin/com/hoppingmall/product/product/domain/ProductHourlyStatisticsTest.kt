package com.hoppingmall.product.product.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("ProductHourlyStatistics 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductHourlyStatisticsTest {

    @Test
    fun 시간별_통계를_생성한다() {
        val hourly = ProductHourlyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.of(2026, 1, 1), hour = 14,
            hourlySalesQuantity = 10, hourlySalesAmount = BigDecimal("100000"),
            hourlyOrderCount = 5
        )

        assertThat(hourly.productId).isEqualTo(1L)
        assertThat(hourly.hour).isEqualTo(14)
        assertThat(hourly.hourlySalesQuantity).isEqualTo(10)
    }

    @Test
    fun 기본값으로_시간별_통계를_생성한다() {
        val hourly = ProductHourlyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.of(2026, 1, 1), hour = 0
        )

        assertThat(hourly.hourlySalesQuantity).isEqualTo(0)
        assertThat(hourly.hourlySalesAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun ProductStatistics로부터_시간별_통계를_업데이트한다() {
        val hourly = ProductHourlyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.of(2026, 1, 1), hour = 14
        )
        val stats = ProductStatistics.create(
            productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L
        )
        stats.incrementSales(10, BigDecimal("100000"))

        hourly.updateFromStatistics(stats)

        assertThat(hourly.hourlySalesQuantity).isEqualTo(10)
        assertThat(hourly.hourlySalesAmount).isEqualByComparingTo(BigDecimal("100000"))
        assertThat(hourly.hourlyOrderCount).isEqualTo(1)
    }
}
