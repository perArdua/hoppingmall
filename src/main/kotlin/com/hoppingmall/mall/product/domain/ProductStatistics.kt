package com.hoppingmall.mall.product.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Entity
@Table(name = "product_statistics")
class ProductStatistics private constructor(
    @Column(unique = true, nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var productName: String,

    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    var categoryId: Long,

    @Column(nullable = false)
    var totalSalesQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var totalSalesAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var totalRefundQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var totalRefundAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var currentCartCount: Long = 0,

    @Column(nullable = false)
    var currentStock: Int = 0,

    @Column(nullable = false, precision = 5, scale = 4)
    var refundRate: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 10, scale = 4)
    var stockTurnoverRate: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var lastAggregatedAt: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {

    fun update(
        productName: String,
        categoryId: Long,
        totalSalesQuantity: Long,
        totalSalesAmount: BigDecimal,
        totalRefundQuantity: Long,
        totalRefundAmount: BigDecimal,
        currentCartCount: Long,
        currentStock: Int
    ) {
        this.productName = productName
        this.categoryId = categoryId
        this.totalSalesQuantity = totalSalesQuantity
        this.totalSalesAmount = totalSalesAmount
        this.totalRefundQuantity = totalRefundQuantity
        this.totalRefundAmount = totalRefundAmount
        this.currentCartCount = currentCartCount
        this.currentStock = currentStock
        this.refundRate = calculateRefundRate(totalRefundQuantity, totalSalesQuantity)
        this.stockTurnoverRate = calculateStockTurnoverRate(totalSalesQuantity, currentStock)
        this.lastAggregatedAt = LocalDateTime.now()
    }

    companion object {
        fun create(
            productId: Long,
            productName: String,
            sellerId: Long,
            categoryId: Long,
            totalSalesQuantity: Long,
            totalSalesAmount: BigDecimal,
            totalRefundQuantity: Long,
            totalRefundAmount: BigDecimal,
            currentCartCount: Long,
            currentStock: Int
        ): ProductStatistics {
            return ProductStatistics(
                productId = productId,
                productName = productName,
                sellerId = sellerId,
                categoryId = categoryId,
                totalSalesQuantity = totalSalesQuantity,
                totalSalesAmount = totalSalesAmount,
                totalRefundQuantity = totalRefundQuantity,
                totalRefundAmount = totalRefundAmount,
                currentCartCount = currentCartCount,
                currentStock = currentStock,
                refundRate = calculateRefundRate(totalRefundQuantity, totalSalesQuantity),
                stockTurnoverRate = calculateStockTurnoverRate(totalSalesQuantity, currentStock)
            )
        }

        private fun calculateRefundRate(refundQuantity: Long, salesQuantity: Long): BigDecimal {
            if (salesQuantity == 0L) return BigDecimal.ZERO
            return BigDecimal(refundQuantity)
                .divide(BigDecimal(salesQuantity), 4, RoundingMode.HALF_UP)
        }

        private fun calculateStockTurnoverRate(salesQuantity: Long, currentStock: Int): BigDecimal {
            if (currentStock == 0) return BigDecimal.ZERO
            return BigDecimal(salesQuantity)
                .divide(BigDecimal(currentStock), 4, RoundingMode.HALF_UP)
        }
    }
}
