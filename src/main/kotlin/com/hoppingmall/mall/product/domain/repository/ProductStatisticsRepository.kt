package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.product.domain.ProductStatistics
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface ProductStatisticsRepository : JpaRepository<ProductStatistics, Long> {
    fun findByProductId(productId: Long): ProductStatistics?
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<ProductStatistics>
    fun findByCategoryId(categoryId: Long, pageable: Pageable): Page<ProductStatistics>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProductStatistics ps WHERE ps.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): ProductStatistics?

    @Query("SELECT COUNT(ps) FROM ProductStatistics ps")
    fun countAllProducts(): Long

    @Query("SELECT COALESCE(SUM(ps.totalSalesAmount), 0) FROM ProductStatistics ps")
    fun sumTotalSalesAmount(): BigDecimal

    @Query("SELECT COALESCE(SUM(ps.totalRefundAmount), 0) FROM ProductStatistics ps")
    fun sumTotalRefundAmount(): BigDecimal

    @Query("SELECT COALESCE(AVG(ps.refundRate), 0) FROM ProductStatistics ps WHERE ps.totalSalesQuantity > 0")
    fun avgRefundRate(): BigDecimal

    @Query("SELECT COALESCE(SUM(ps.todaySalesAmount), 0) FROM ProductStatistics ps")
    fun sumTodaySalesAmount(): BigDecimal

    @Query("SELECT COALESCE(SUM(ps.todayOrderCount), 0) FROM ProductStatistics ps")
    fun sumTodayOrderCount(): Long

    @Query("SELECT COALESCE(SUM(ps.todayRefundAmount), 0) FROM ProductStatistics ps")
    fun sumTodayRefundAmount(): BigDecimal
}
