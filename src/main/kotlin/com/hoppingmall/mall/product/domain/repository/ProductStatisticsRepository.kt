package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.product.domain.ProductStatistics
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface ProductStatisticsRepository : JpaRepository<ProductStatistics, Long> {
    fun findByProductId(productId: Long): ProductStatistics?
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<ProductStatistics>
    fun findByCategoryId(categoryId: Long, pageable: Pageable): Page<ProductStatistics>

    @Query("SELECT COUNT(ps) FROM ProductStatistics ps")
    fun countAllProducts(): Long

    @Query("SELECT COALESCE(SUM(ps.totalSalesAmount), 0) FROM ProductStatistics ps")
    fun sumTotalSalesAmount(): BigDecimal

    @Query("SELECT COALESCE(SUM(ps.totalRefundAmount), 0) FROM ProductStatistics ps")
    fun sumTotalRefundAmount(): BigDecimal

    @Query("SELECT COALESCE(AVG(ps.refundRate), 0) FROM ProductStatistics ps WHERE ps.totalSalesQuantity > 0")
    fun avgRefundRate(): BigDecimal
}
