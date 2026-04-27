package com.hoppingmall.order.cartItem.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(
    name = "cart_items",
    indexes = [
        Index(name = "idx_cart_items_buyer_id", columnList = "buyerId"),
        Index(name = "idx_cart_items_product_id", columnList = "productId")
    ]
)
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
    var quantity: Int,

    @Column(precision = 19, scale = 2)
    var totalPrice: BigDecimal,
): BaseEntity() {

    fun updateQuantity(newQuantity: Int) {
        this.quantity = newQuantity
        this.totalPrice = productPrice.multiply(BigDecimal(newQuantity))
    }
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
