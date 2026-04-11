package com.hoppingmall.product.wishlist.dto.response

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.wishlist.domain.Wishlist
import java.math.BigDecimal
import java.time.LocalDateTime

data class WishlistResponse(
    val id: Long,
    val productId: Long,
    val productName: String?,
    val productPrice: BigDecimal?,
    val productStatus: ProductStatus?,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(wishlist: Wishlist, product: Product?): WishlistResponse {
            return WishlistResponse(
                id = wishlist.id!!,
                productId = wishlist.productId,
                productName = product?.name,
                productPrice = product?.price,
                productStatus = product?.status,
                createdAt = wishlist.createdAt
            )
        }
    }
}
