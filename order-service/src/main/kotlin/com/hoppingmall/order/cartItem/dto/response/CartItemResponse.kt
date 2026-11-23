package com.hoppingmall.order.cartItem.dto.response

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.enum.CartProductStatus
import com.hoppingmall.order.port.ProductInfo
import java.math.BigDecimal

data class CartItemResponse(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productPrice: BigDecimal,
    val productImageUrl: String?,
    val quantity: Int,
    val totalPrice: BigDecimal,
    val currentPrice: BigDecimal? = null,
    val priceChanged: Boolean = false,
    val productStatus: CartProductStatus? = null,
    val inStock: Boolean? = null,
    val productChecked: Boolean = false
) {
    companion object {
        fun from(cartItem: CartItem): CartItemResponse =
            build(cartItem, currentPrice = null, productStatus = null, inStock = null, productChecked = false)

        fun of(cartItem: CartItem, product: ProductInfo): CartItemResponse {
            val productStatus = CartProductStatus.of(product.status)
            return build(
                cartItem,
                currentPrice = product.price,
                productStatus = productStatus,
                inStock = product.stockQuantity?.let { productStatus != CartProductStatus.SOLD_OUT && it >= cartItem.quantity },
                productChecked = true
            )
        }

        fun deleted(cartItem: CartItem): CartItemResponse =
            build(cartItem, currentPrice = null, productStatus = CartProductStatus.DELETED, inStock = false, productChecked = true)

        private fun build(
            cartItem: CartItem,
            currentPrice: BigDecimal?,
            productStatus: CartProductStatus?,
            inStock: Boolean?,
            productChecked: Boolean
        ) = CartItemResponse(
            id = cartItem.id!!,
            productId = cartItem.productId,
            productName = cartItem.productName,
            productPrice = cartItem.productPrice,
            productImageUrl = cartItem.productImageUrl,
            quantity = cartItem.quantity,
            totalPrice = currentPrice?.multiply(BigDecimal(cartItem.quantity)) ?: cartItem.totalPrice,
            currentPrice = currentPrice,
            priceChanged = currentPrice != null && currentPrice.compareTo(cartItem.productPrice) != 0,
            productStatus = productStatus,
            inStock = inStock,
            productChecked = productChecked
        )
    }
}
