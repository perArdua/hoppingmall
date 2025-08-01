package com.hoppingmall.mall.cartItem.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

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

    @Column
    val productPrice: Long,

    @Column
    val productImageUrl: String?,

    @Column
    val quantity: Int,
    
    @Column
    val totalPrice: Long,
): BaseEntity() {
    companion object {
        fun create(
            buyerId: Long,
            productId: Long,
            productName: String,
            productPrice: Long,
            productImageUrl: String?,
            quantity: Int
        ): CartItem {
            val totalPrice = productPrice * quantity
            return CartItem(buyerId, productId, productName, productPrice, productImageUrl, quantity, totalPrice)
        }
    }
}