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
    var todaySalesQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var todaySalesAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var todayOrderCount: Long = 0,

    @Column(nullable = false)
    var todayRefundQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var todayRefundAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 15, scale = 2)
    var last7DaysSalesAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 15, scale = 2)
    var last30DaysSalesAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 10, scale = 4)
    var salesGrowthRate: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var orderCount: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var netRevenue: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 15, scale = 2)
    var averageOrderAmount: BigDecimal = BigDecimal.ZERO,

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
        this.netRevenue = totalSalesAmount.subtract(totalRefundAmount)
        this.averageOrderAmount = calculateAverageOrderAmount(totalSalesAmount, orderCount)
        this.lastAggregatedAt = LocalDateTime.now()
    }

    fun incrementSales(quantity: Long, amount: BigDecimal) {
        this.totalSalesQuantity += quantity
        this.totalSalesAmount = this.totalSalesAmount.add(amount)
        this.todaySalesQuantity += quantity
        this.todaySalesAmount = this.todaySalesAmount.add(amount)
        this.todayOrderCount++
        this.orderCount++
        this.refundRate = calculateRefundRate(this.totalRefundQuantity, this.totalSalesQuantity)
        this.stockTurnoverRate = calculateStockTurnoverRate(this.totalSalesQuantity, this.currentStock)
        this.netRevenue = this.totalSalesAmount.subtract(this.totalRefundAmount)
        this.averageOrderAmount = calculateAverageOrderAmount(this.totalSalesAmount, this.orderCount)
        this.lastAggregatedAt = LocalDateTime.now()
    }

    fun decrementSales(quantity: Long, amount: BigDecimal) {
        this.totalSalesQuantity = maxOf(0, this.totalSalesQuantity - quantity)
        this.totalSalesAmount = this.totalSalesAmount.subtract(amount).coerceAtLeast(BigDecimal.ZERO)
        this.todaySalesQuantity = maxOf(0, this.todaySalesQuantity - quantity)
        this.todaySalesAmount = this.todaySalesAmount.subtract(amount).coerceAtLeast(BigDecimal.ZERO)
        this.todayOrderCount = maxOf(0, this.todayOrderCount - 1)
        this.orderCount = maxOf(0, this.orderCount - 1)
        this.refundRate = calculateRefundRate(this.totalRefundQuantity, this.totalSalesQuantity)
        this.stockTurnoverRate = calculateStockTurnoverRate(this.totalSalesQuantity, this.currentStock)
        this.netRevenue = this.totalSalesAmount.subtract(this.totalRefundAmount)
        this.averageOrderAmount = calculateAverageOrderAmount(this.totalSalesAmount, this.orderCount)
        this.lastAggregatedAt = LocalDateTime.now()
    }

    fun incrementRefund(quantity: Long, amount: BigDecimal) {
        this.totalRefundQuantity += quantity
        this.totalRefundAmount = this.totalRefundAmount.add(amount)
        this.todayRefundQuantity += quantity
        this.todayRefundAmount = this.todayRefundAmount.add(amount)
        this.refundRate = calculateRefundRate(this.totalRefundQuantity, this.totalSalesQuantity)
        this.netRevenue = this.totalSalesAmount.subtract(this.totalRefundAmount)
        this.lastAggregatedAt = LocalDateTime.now()
    }

    fun resetToday() {
        this.todaySalesQuantity = 0
        this.todaySalesAmount = BigDecimal.ZERO
        this.todayOrderCount = 0
        this.todayRefundQuantity = 0
        this.todayRefundAmount = BigDecimal.ZERO
    }

    fun updatePeriodMetrics(last7Days: BigDecimal, last30Days: BigDecimal, previousWeekAmount: BigDecimal) {
        this.last7DaysSalesAmount = last7Days
        this.last30DaysSalesAmount = last30Days
        this.salesGrowthRate = if (previousWeekAmount > BigDecimal.ZERO) {
            last7Days.subtract(previousWeekAmount)
                .divide(previousWeekAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else {
            BigDecimal.ZERO
        }
    }

    fun updateCartAndInventory(cartCount: Long, stock: Int) {
        this.currentCartCount = cartCount
        this.currentStock = stock
        this.stockTurnoverRate = calculateStockTurnoverRate(this.totalSalesQuantity, stock)
        this.lastAggregatedAt = LocalDateTime.now()
    }

    companion object {
        fun create(
            productId: Long,
            productName: String,
            sellerId: Long,
            categoryId: Long,
            totalSalesQuantity: Long = 0,
            totalSalesAmount: BigDecimal = BigDecimal.ZERO,
            totalRefundQuantity: Long = 0,
            totalRefundAmount: BigDecimal = BigDecimal.ZERO,
            currentCartCount: Long = 0,
            currentStock: Int = 0
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
                stockTurnoverRate = calculateStockTurnoverRate(totalSalesQuantity, currentStock),
                netRevenue = totalSalesAmount.subtract(totalRefundAmount),
                averageOrderAmount = BigDecimal.ZERO
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

        private fun calculateAverageOrderAmount(totalAmount: BigDecimal, orderCount: Long): BigDecimal {
            if (orderCount == 0L) return BigDecimal.ZERO
            return totalAmount.divide(BigDecimal(orderCount), 2, RoundingMode.HALF_UP)
        }
    }
}
