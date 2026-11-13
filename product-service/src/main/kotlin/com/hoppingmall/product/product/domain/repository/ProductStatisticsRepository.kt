package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.dto.TopSellingProductDto
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface ProductStatisticsRepository : JpaRepository<ProductStatistics, Long> {
    fun findByProductId(productId: Long): ProductStatistics?
    fun findBySellerId(sellerId: Long, pageable: Pageable): Slice<ProductStatistics>
    fun findByCategoryId(categoryId: Long, pageable: Pageable): Slice<ProductStatistics>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProductStatistics ps WHERE ps.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): ProductStatistics?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE ProductStatistics ps
        SET ps.totalSalesQuantity = ps.totalSalesQuantity + :quantity,
            ps.totalSalesAmount = ps.totalSalesAmount + :amount,
            ps.todaySalesQuantity = ps.todaySalesQuantity + :quantity,
            ps.todaySalesAmount = ps.todaySalesAmount + :amount,
            ps.todayOrderCount = ps.todayOrderCount + 1,
            ps.orderCount = ps.orderCount + 1
        WHERE ps.productId = :productId
        """
    )
    fun addSales(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Long,
        @Param("amount") amount: BigDecimal
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE ProductStatistics ps
        SET ps.totalSalesQuantity = CASE WHEN ps.totalSalesQuantity > :quantity THEN ps.totalSalesQuantity - :quantity ELSE 0 END,
            ps.totalSalesAmount = CASE WHEN ps.totalSalesAmount > :amount THEN ps.totalSalesAmount - :amount ELSE 0 END,
            ps.todaySalesQuantity = CASE WHEN ps.todaySalesQuantity > :quantity THEN ps.todaySalesQuantity - :quantity ELSE 0 END,
            ps.todaySalesAmount = CASE WHEN ps.todaySalesAmount > :amount THEN ps.todaySalesAmount - :amount ELSE 0 END,
            ps.todayOrderCount = CASE WHEN ps.todayOrderCount > 0 THEN ps.todayOrderCount - 1 ELSE 0 END,
            ps.orderCount = CASE WHEN ps.orderCount > 0 THEN ps.orderCount - 1 ELSE 0 END
        WHERE ps.productId = :productId
        """
    )
    fun subtractSales(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Long,
        @Param("amount") amount: BigDecimal
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE ProductStatistics ps
        SET ps.totalRefundQuantity = ps.totalRefundQuantity + :quantity,
            ps.totalRefundAmount = ps.totalRefundAmount + :amount,
            ps.todayRefundQuantity = ps.todayRefundQuantity + :quantity,
            ps.todayRefundAmount = ps.todayRefundAmount + :amount
        WHERE ps.productId = :productId
        """
    )
    fun addRefund(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Long,
        @Param("amount") amount: BigDecimal
    ): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProductStatistics ps WHERE ps.id IN :ids ORDER BY ps.id")
    fun findAllByIdInForUpdate(@Param("ids") ids: Collection<Long>): List<ProductStatistics>

    @Query("SELECT ps.id FROM ProductStatistics ps ORDER BY ps.id")
    fun findIdSlice(pageable: Pageable): Slice<Long>

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

    @Query("""
        SELECT COUNT(ps) AS totalProducts,
               COALESCE(SUM(ps.todaySalesAmount), 0) AS todaySalesAmount,
               COALESCE(SUM(ps.todayOrderCount), 0) AS todayOrderCount,
               COALESCE(SUM(ps.todayRefundAmount), 0) AS todayRefundAmount
        FROM ProductStatistics ps WHERE ps.sellerId = :sellerId
    """)
    fun findSellerTodaySummary(@Param("sellerId") sellerId: Long): SellerTodaySummaryProjection

    @Query("""
        SELECT new com.hoppingmall.product.product.dto.TopSellingProductDto(
            ps.productId, ps.productName
        )
        FROM ProductStatistics ps WHERE ps.sellerId = :sellerId ORDER BY ps.todaySalesAmount DESC LIMIT 1
    """)
    fun findTopSellingBySellerId(@Param("sellerId") sellerId: Long): TopSellingProductDto?

    @Query("SELECT ps FROM ProductStatistics ps WHERE ps.todaySalesQuantity > 0 OR ps.todayRefundQuantity > 0")
    fun findAllActive(): List<ProductStatistics>

    @Query("SELECT ps FROM ProductStatistics ps WHERE ps.todaySalesQuantity > 0 OR ps.todayRefundQuantity > 0")
    fun findAllActive(pageable: Pageable): Slice<ProductStatistics>
}
