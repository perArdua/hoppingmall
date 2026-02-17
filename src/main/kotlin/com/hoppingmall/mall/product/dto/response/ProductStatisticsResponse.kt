package com.hoppingmall.mall.product.dto.response

import com.hoppingmall.mall.product.domain.ProductStatistics
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
                lastAggregatedAt = entity.lastAggregatedAt
            )
        }
    }
}
