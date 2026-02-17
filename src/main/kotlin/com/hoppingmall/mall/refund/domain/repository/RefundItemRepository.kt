package com.hoppingmall.mall.refund.domain.repository

import com.hoppingmall.mall.product.dto.RefundAggregation
import com.hoppingmall.mall.refund.domain.RefundItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RefundItemRepository : JpaRepository<RefundItem, Long> {
    fun findByRefundId(refundId: Long): List<RefundItem>

    @Query(
        value = """
            SELECT ri.product_id AS productId,
                   SUM(ri.quantity) AS totalQuantity,
                   SUM(ri.refund_price) AS totalAmount
            FROM refund_items ri
            JOIN refunds r ON ri.refund_id = r.id
            WHERE r.status = :status
              AND ri.deleted_at IS NULL
              AND r.deleted_at IS NULL
            GROUP BY ri.product_id
        """,
        nativeQuery = true
    )
    fun aggregateRefundsByProduct(@Param("status") status: String): List<RefundAggregation>
}
