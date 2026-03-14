package com.hoppingmall.order.refund.domain

import com.hoppingmall.order.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.math.BigDecimal

@Entity
@Table(
    name = "refund_items",
    indexes = [Index(name = "idx_refund_items_refund_id", columnList = "refundId")]
)
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class RefundItem private constructor(
    @Column(nullable = false)
    val refundId: Long,

    @Column(nullable = false)
    val orderItemId: Long,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    val productName: String,

    @Column(nullable = false, precision = 10, scale = 2)
    val productPrice: BigDecimal,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val refundPrice: BigDecimal
) : BaseEntity() {

    companion object {
        fun create(
            refundId: Long,
            orderItemId: Long,
            productId: Long,
            productName: String,
            productPrice: BigDecimal,
            quantity: Int
        ): RefundItem {
            return RefundItem(
                refundId = refundId,
                orderItemId = orderItemId,
                productId = productId,
                productName = productName,
                productPrice = productPrice,
                quantity = quantity,
                refundPrice = productPrice.multiply(BigDecimal(quantity))
            )
        }
    }
}
