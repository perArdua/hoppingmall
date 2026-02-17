package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.product.domain.ProductDailyStatistics
import java.math.BigDecimal
import java.time.LocalDate

fun ProductDailyStatistics.Companion.fixture(
    productId: Long = 1L,
    statisticsDate: LocalDate = LocalDate.of(2026, 2, 18),
    dailySalesQuantity: Long = 50,
    dailySalesAmount: BigDecimal = BigDecimal("500000"),
    dailyOrderCount: Long = 10,
    dailyRefundQuantity: Long = 2,
    dailyRefundAmount: BigDecimal = BigDecimal("20000"),
    endOfDayStock: Int = 100
): ProductDailyStatistics {
    return ProductDailyStatistics.create(
        productId = productId,
        statisticsDate = statisticsDate,
        dailySalesQuantity = dailySalesQuantity,
        dailySalesAmount = dailySalesAmount,
        dailyOrderCount = dailyOrderCount,
        dailyRefundQuantity = dailyRefundQuantity,
        dailyRefundAmount = dailyRefundAmount,
        endOfDayStock = endOfDayStock
    )
}
