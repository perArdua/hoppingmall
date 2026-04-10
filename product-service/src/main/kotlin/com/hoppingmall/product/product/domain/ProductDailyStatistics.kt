package com.hoppingmall.product.product.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "product_daily_statistics",
    uniqueConstraints = [UniqueConstraint(columnNames = ["product_id", "statistics_date"])],
    indexes = [Index(name = "idx_daily_stats_date", columnList = "statistics_date")]
)
class ProductDailyStatistics private constructor(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "statistics_date", nullable = false)
    val statisticsDate: LocalDate,

    @Column(nullable = false)
    var dailySalesQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var dailySalesAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var dailyOrderCount: Long = 0,

    @Column(nullable = false)
    var dailyRefundQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var dailyRefundAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var endOfDayStock: Int = 0
) : BaseEntity() {

    fun updateFromStatistics(stats: ProductStatistics) {
        this.dailySalesQuantity = stats.todaySalesQuantity
        this.dailySalesAmount = stats.todaySalesAmount
        this.dailyOrderCount = stats.todayOrderCount
        this.dailyRefundQuantity = stats.todayRefundQuantity
        this.dailyRefundAmount = stats.todayRefundAmount
        this.endOfDayStock = stats.currentStock
    }

    companion object {
        fun create(
            productId: Long,
            statisticsDate: LocalDate,
            dailySalesQuantity: Long = 0,
            dailySalesAmount: BigDecimal = BigDecimal.ZERO,
            dailyOrderCount: Long = 0,
            dailyRefundQuantity: Long = 0,
            dailyRefundAmount: BigDecimal = BigDecimal.ZERO,
            endOfDayStock: Int = 0
        ): ProductDailyStatistics {
            return ProductDailyStatistics(
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
    }
}
