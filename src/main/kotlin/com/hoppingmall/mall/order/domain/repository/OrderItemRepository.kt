package com.hoppingmall.mall.order.domain.repository

import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.product.dto.SalesAggregation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findByOrderId(orderId: Long): List<OrderItem>

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
