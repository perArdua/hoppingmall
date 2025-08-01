package com.hoppingmall.mall.cartItem.dto.response

import com.hoppingmall.mall.cartItem.domain.CartItem

data class CartItemResponse(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productPrice: Long,
    val productImageUrl: String?,
    val quantity: Int,
    val totalPrice: Long
) {
    companion object {
        fun from(cartItem: CartItem): CartItemResponse {
            return CartItemResponse(
                id = cartItem.id!!,
                productId = cartItem.productId,
                productName = cartItem.productName,
                productPrice = cartItem.productPrice,
                productImageUrl = cartItem.productImageUrl,
                quantity = cartItem.quantity,
                totalPrice = cartItem.totalPrice
            )
        }
    }
} 