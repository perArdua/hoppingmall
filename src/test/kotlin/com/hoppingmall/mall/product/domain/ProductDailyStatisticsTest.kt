package com.hoppingmall.mall.product.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("ProductDailyStatistics")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductDailyStatisticsTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 일별_통계가_기본값으로_생성된다() {
            val daily = ProductDailyStatistics.create(
                productId = 1L,
                statisticsDate = LocalDate.of(2026, 2, 18)
            )

            assertEquals(1L, daily.productId)
            assertEquals(LocalDate.of(2026, 2, 18), daily.statisticsDate)
            assertEquals(0, daily.dailySalesQuantity)
            assertEquals(BigDecimal.ZERO, daily.dailySalesAmount)
            assertEquals(0, daily.dailyOrderCount)
            assertEquals(0, daily.dailyRefundQuantity)
            assertEquals(BigDecimal.ZERO, daily.dailyRefundAmount)
            assertEquals(0, daily.endOfDayStock)
        }

        @Test
        fun 일별_통계가_지정된_값으로_생성된다() {
            val daily = ProductDailyStatistics.create(
                productId = 1L,
                statisticsDate = LocalDate.of(2026, 2, 18),
                dailySalesQuantity = 50,
                dailySalesAmount = BigDecimal("500000"),
                dailyOrderCount = 10,
                dailyRefundQuantity = 2,
                dailyRefundAmount = BigDecimal("20000"),
                endOfDayStock = 100
            )

            assertEquals(50, daily.dailySalesQuantity)
            assertEquals(BigDecimal("500000"), daily.dailySalesAmount)
            assertEquals(10, daily.dailyOrderCount)
            assertEquals(2, daily.dailyRefundQuantity)
            assertEquals(BigDecimal("20000"), daily.dailyRefundAmount)
            assertEquals(100, daily.endOfDayStock)
        }
    }

    @Nested
    @DisplayName("updateFromStatistics")
    inner class UpdateFromStatistics {
        @Test
        fun ProductStatistics에서_오늘_데이터를_복사한다() {
            val stats = ProductStatistics.create(
                productId = 1L,
                productName = "테스트 상품",
                sellerId = 1L,
                categoryId = 1L,
                currentStock = 80
            )
            stats.incrementSales(10, BigDecimal("100000"))
            stats.incrementRefund(1, BigDecimal("10000"))

            val daily = ProductDailyStatistics.create(
                productId = 1L,
                statisticsDate = LocalDate.of(2026, 2, 18)
            )

            daily.updateFromStatistics(stats)

            assertEquals(10, daily.dailySalesQuantity)
            assertEquals(BigDecimal("100000"), daily.dailySalesAmount)
            assertEquals(1, daily.dailyOrderCount)
            assertEquals(1, daily.dailyRefundQuantity)
            assertEquals(BigDecimal("10000"), daily.dailyRefundAmount)
            assertEquals(80, daily.endOfDayStock)
        }
    }
}
