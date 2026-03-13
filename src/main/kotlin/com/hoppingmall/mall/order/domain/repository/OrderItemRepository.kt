package com.hoppingmall.mall.order.domain.repository

import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.product.dto.SalesAggregation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findByOrderId(orderId: Long): List<OrderItem>

    fun findByOrderIdIn(orderIds: List<Long>): List<OrderItem>

    @Query(
        value = """
            SELECT oi.* FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            WHERE oi.seller_id = :sellerId
              AND o.status = 'DELIVERED'
              AND o.updated_at BETWEEN :startDate AND :endDate
              AND oi.deleted_at IS NULL
              AND o.deleted_at IS NULL
        """,
        nativeQuery = true
    )
    fun findDeliveredItemsBySellerAndPeriod(
        @Param("sellerId") sellerId: Long,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<OrderItem>

    @Query(
        value = """
            SELECT oi.product_id AS productId,
                   SUM(oi.quantity) AS totalQuantity,
                   SUM(oi.total_price) AS totalAmount
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            WHERE o.status IN (:statuses)
              AND oi.deleted_at IS NULL
              AND o.deleted_at IS NULL
            GROUP BY oi.product_id
        """,
        nativeQuery = true
    )
    fun aggregateSalesByProduct(@Param("statuses") statuses: List<String>): List<SalesAggregation>
}
