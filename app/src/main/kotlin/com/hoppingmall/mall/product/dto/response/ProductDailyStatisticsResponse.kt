package com.hoppingmall.mall.product.dto.response

import com.hoppingmall.mall.product.domain.ProductDailyStatistics
import java.math.BigDecimal
import java.time.LocalDate

data class ProductDailyStatisticsResponse(
    val productId: Long,
    val statisticsDate: LocalDate,
    val dailySalesQuantity: Long,
    val dailySalesAmount: BigDecimal,
    val dailyOrderCount: Long,
    val dailyRefundQuantity: Long,
    val dailyRefundAmount: BigDecimal,
    val endOfDayStock: Int
) {
    companion object {
        fun from(entity: ProductDailyStatistics): ProductDailyStatisticsResponse {
            return ProductDailyStatisticsResponse(
                productId = entity.productId,
                statisticsDate = entity.statisticsDate,
                dailySalesQuantity = entity.dailySalesQuantity,
                dailySalesAmount = entity.dailySalesAmount,
                dailyOrderCount = entity.dailyOrderCount,
                dailyRefundQuantity = entity.dailyRefundQuantity,
                dailyRefundAmount = entity.dailyRefundAmount,
                endOfDayStock = entity.endOfDayStock
            )
        }
    }
}
