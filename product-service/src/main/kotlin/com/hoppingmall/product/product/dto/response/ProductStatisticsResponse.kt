package com.hoppingmall.product.product.dto.response

import com.hoppingmall.product.product.domain.ProductStatistics
import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductStatisticsResponse(
    val productId: Long,
    val productName: String,
    val sellerId: Long,
    val categoryId: Long,
    val totalSalesQuantity: Long,
    val totalSalesAmount: BigDecimal,
    val totalRefundQuantity: Long,
    val totalRefundAmount: BigDecimal,
    val currentCartCount: Long,
    val currentStock: Int,
    val refundRate: BigDecimal,
    val stockTurnoverRate: BigDecimal,
    val todaySalesQuantity: Long,
    val todaySalesAmount: BigDecimal,
    val todayOrderCount: Long,
    val todayRefundQuantity: Long,
    val todayRefundAmount: BigDecimal,
    val last7DaysSalesAmount: BigDecimal,
    val last30DaysSalesAmount: BigDecimal,
    val salesGrowthRate: BigDecimal,
    val orderCount: Long,
    val netRevenue: BigDecimal,
    val averageOrderAmount: BigDecimal,
    val lastAggregatedAt: LocalDateTime
) {
    companion object {
        fun from(entity: ProductStatistics): ProductStatisticsResponse {
            return ProductStatisticsResponse(
                productId = entity.productId,
                productName = entity.productName,
                sellerId = entity.sellerId,
                categoryId = entity.categoryId,
                totalSalesQuantity = entity.totalSalesQuantity,
                totalSalesAmount = entity.totalSalesAmount,
                totalRefundQuantity = entity.totalRefundQuantity,
                totalRefundAmount = entity.totalRefundAmount,
                currentCartCount = entity.currentCartCount,
                currentStock = entity.currentStock,
                refundRate = entity.refundRate,
                stockTurnoverRate = entity.stockTurnoverRate,
                todaySalesQuantity = entity.todaySalesQuantity,
                todaySalesAmount = entity.todaySalesAmount,
                todayOrderCount = entity.todayOrderCount,
                todayRefundQuantity = entity.todayRefundQuantity,
                todayRefundAmount = entity.todayRefundAmount,
                last7DaysSalesAmount = entity.last7DaysSalesAmount,
                last30DaysSalesAmount = entity.last30DaysSalesAmount,
                salesGrowthRate = entity.salesGrowthRate,
                orderCount = entity.orderCount,
                netRevenue = entity.netRevenue,
                averageOrderAmount = entity.averageOrderAmount,
                lastAggregatedAt = entity.lastAggregatedAt
            )
        }
    }
}
