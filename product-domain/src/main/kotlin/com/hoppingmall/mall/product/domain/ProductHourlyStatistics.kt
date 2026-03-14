package com.hoppingmall.mall.product.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "product_hourly_statistics",
    uniqueConstraints = [UniqueConstraint(columnNames = ["product_id", "statistics_date", "hour"])]
)
class ProductHourlyStatistics(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "statistics_date", nullable = false)
    val statisticsDate: LocalDate,

    @Column(name = "\"hour\"", nullable = false)
    val hour: Int,

    @Column(nullable = false)
    var hourlySalesQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var hourlySalesAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var hourlyOrderCount: Long = 0,

    @Column(nullable = false)
    var hourlyRefundQuantity: Long = 0,

    @Column(nullable = false, precision = 15, scale = 2)
    var hourlyRefundAmount: BigDecimal = BigDecimal.ZERO
) : BaseEntity() {

    fun updateFromStatistics(stats: ProductStatistics) {
        this.hourlySalesQuantity = stats.todaySalesQuantity
        this.hourlySalesAmount = stats.todaySalesAmount
        this.hourlyOrderCount = stats.todayOrderCount
        this.hourlyRefundQuantity = stats.todayRefundQuantity
        this.hourlyRefundAmount = stats.todayRefundAmount
    }

    companion object {
        fun create(
            productId: Long,
            statisticsDate: LocalDate,
            hour: Int,
            hourlySalesQuantity: Long = 0,
            hourlySalesAmount: BigDecimal = BigDecimal.ZERO,
            hourlyOrderCount: Long = 0,
            hourlyRefundQuantity: Long = 0,
            hourlyRefundAmount: BigDecimal = BigDecimal.ZERO
        ): ProductHourlyStatistics {
            return ProductHourlyStatistics(
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
    }
}
