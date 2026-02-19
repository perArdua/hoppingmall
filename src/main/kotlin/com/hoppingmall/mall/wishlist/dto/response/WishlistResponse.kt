package com.hoppingmall.mall.wishlist.dto.response

import com.hoppingmall.mall.wishlist.domain.Wishlist
import java.time.LocalDateTime

data class WishlistResponse(
    val id: Long,
    val productId: Long,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(wishlist: Wishlist): WishlistResponse {
            return WishlistResponse(
                id = wishlist.id!!,
                productId = wishlist.productId,
                createdAt = wishlist.createdAt
            )
        }
    }
}
