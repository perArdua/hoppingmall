package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.product.domain.ProductHourlyStatistics
import java.math.BigDecimal
import java.time.LocalDate

fun ProductHourlyStatistics.Companion.fixture(
    productId: Long = 1L,
    statisticsDate: LocalDate = LocalDate.of(2026, 3, 13),
    hour: Int = 14,
    hourlySalesQuantity: Long = 30,
    hourlySalesAmount: BigDecimal = BigDecimal("300000"),
    hourlyOrderCount: Long = 8,
    hourlyRefundQuantity: Long = 1,
    hourlyRefundAmount: BigDecimal = BigDecimal("10000")
): ProductHourlyStatistics {
    return ProductHourlyStatistics.create(
        productId = productId,
        statisticsDate = statisticsDate,
        hour = hour,
        hourlySalesQuantity = hourlySalesQuantity,
        hourlySalesAmount = hourlySalesAmount,
        hourlyOrderCount = hourlyOrderCount,
        hourlyRefundQuantity = hourlyRefundQuantity,
        hourlyRefundAmount = hourlyRefundAmount
    )
}
