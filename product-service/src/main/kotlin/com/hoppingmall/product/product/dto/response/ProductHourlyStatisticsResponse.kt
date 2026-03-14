package com.hoppingmall.product.product.dto.response

import com.hoppingmall.product.product.domain.ProductHourlyStatistics
import java.math.BigDecimal
import java.time.LocalDate

data class ProductHourlyStatisticsResponse(
    val productId: Long,
    val statisticsDate: LocalDate,
    val hour: Int,
    val hourlySalesQuantity: Long,
    val hourlySalesAmount: BigDecimal,
    val hourlyOrderCount: Long,
    val hourlyRefundQuantity: Long,
    val hourlyRefundAmount: BigDecimal
) {
    companion object {
        fun from(entity: ProductHourlyStatistics): ProductHourlyStatisticsResponse {
            return ProductHourlyStatisticsResponse(
                productId = entity.productId,
                statisticsDate = entity.statisticsDate,
                hour = entity.hour,
                hourlySalesQuantity = entity.hourlySalesQuantity,
                hourlySalesAmount = entity.hourlySalesAmount,
                hourlyOrderCount = entity.hourlyOrderCount,
                hourlyRefundQuantity = entity.hourlyRefundQuantity,
                hourlyRefundAmount = entity.hourlyRefundAmount
            )
        }
    }
}
