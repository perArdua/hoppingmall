package com.hoppingmall.mall.cartItem.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.math.BigDecimal

@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
@Entity
@Table(name = "cart_items")
class CartItem private constructor(
    @Column
    val buyerId: Long,

    @Column
    val productId: Long,

    @Column
    val productName: String,

    @Column(precision = 10, scale = 2)
    val productPrice: BigDecimal,

    @Column
    val productImageUrl: String?,

    @Column
    val quantity: Int,

    @Column(precision = 10, scale = 2)
    val totalPrice: BigDecimal,
): BaseEntity() {
    companion object {
        fun create(
            buyerId: Long,
            productId: Long,
            productName: String,
            productPrice: BigDecimal,
            productImageUrl: String?,
            quantity: Int
        ): CartItem {
            val totalPrice = productPrice.multiply(BigDecimal(quantity))
            return CartItem(buyerId, productId, productName, productPrice, productImageUrl, quantity, totalPrice)
        }
    }
}