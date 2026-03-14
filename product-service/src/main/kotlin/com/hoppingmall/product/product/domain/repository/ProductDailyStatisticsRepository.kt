package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.ProductDailyStatistics
import com.hoppingmall.product.product.dto.TopProductProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface ProductDailyStatisticsRepository : JpaRepository<ProductDailyStatistics, Long> {

    fun findByProductIdAndStatisticsDate(productId: Long, statisticsDate: LocalDate): ProductDailyStatistics?

    fun findByProductIdAndStatisticsDateBetweenOrderByStatisticsDateAsc(
        productId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ProductDailyStatistics>

    @Query(
        """
        SELECT COALESCE(SUM(d.dailySalesAmount), 0)
        FROM ProductDailyStatistics d
        WHERE d.productId = :productId
          AND d.statisticsDate BETWEEN :startDate AND :endDate
        """
    )
    fun sumSalesAmountByProductIdAndDateRange(
        @Param("productId") productId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): BigDecimal

    @Query(
        """
        SELECT d.productId AS productId,
               SUM(d.dailySalesAmount) AS totalAmount,
               SUM(d.dailySalesQuantity) AS totalQuantity
        FROM ProductDailyStatistics d
        WHERE d.statisticsDate BETWEEN :startDate AND :endDate
        GROUP BY d.productId
        ORDER BY SUM(d.dailySalesAmount) DESC
        LIMIT :limit
        """
    )
    fun findTopSellingProducts(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("limit") limit: Int
    ): List<TopProductProjection>

    @Query(
        """
        SELECT d.productId AS productId,
               SUM(d.dailyRefundAmount) AS totalAmount,
               SUM(d.dailyRefundQuantity) AS totalQuantity
        FROM ProductDailyStatistics d
        WHERE d.statisticsDate BETWEEN :startDate AND :endDate
        GROUP BY d.productId
        ORDER BY SUM(d.dailyRefundAmount) DESC
        LIMIT :limit
        """
    )
    fun findTopRefundProducts(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("limit") limit: Int
    ): List<TopProductProjection>
}
