package com.hoppingmall.mall.order.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.math.BigDecimal

@Entity
@Table(
    name = "order_items",
    indexes = [
        Index(name = "idx_order_items_order_id", columnList = "orderId"),
        Index(name = "idx_order_items_product_id", columnList = "productId")
    ]
)
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class OrderItem private constructor(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    val productName: String,

    @Column(nullable = false, precision = 10, scale = 2)
    val productPrice: BigDecimal,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val totalPrice: BigDecimal
) : BaseEntity() {

    companion object {
        fun create(
            orderId: Long,
            sellerId: Long,
            productId: Long,
            productName: String,
            productPrice: BigDecimal,
            quantity: Int
        ): OrderItem {
            return OrderItem(
                orderId = orderId,
                sellerId = sellerId,
                productId = productId,
                productName = productName,
                productPrice = productPrice,
                quantity = quantity,
                totalPrice = productPrice.multiply(BigDecimal(quantity))
            )
        }
    }
}
