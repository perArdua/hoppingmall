package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.ProductHourlyStatistics
import com.hoppingmall.product.product.dto.HourlyAggregationProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface ProductHourlyStatisticsRepository : JpaRepository<ProductHourlyStatistics, Long> {

    fun findByProductIdAndStatisticsDateAndHour(
        productId: Long,
        statisticsDate: LocalDate,
        hour: Int
    ): ProductHourlyStatistics?

    fun findByProductIdAndStatisticsDateOrderByHourAsc(
        productId: Long,
        statisticsDate: LocalDate
    ): List<ProductHourlyStatistics>

    @Query(
        """
        SELECT h.hour AS hour,
               SUM(h.hourlySalesAmount) AS totalAmount,
               SUM(h.hourlyOrderCount) AS totalOrders
        FROM ProductHourlyStatistics h
        WHERE h.statisticsDate BETWEEN :startDate AND :endDate
        GROUP BY h.hour
        ORDER BY SUM(h.hourlySalesAmount) DESC
        """
    )
    fun findPeakHours(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<HourlyAggregationProjection>
}
