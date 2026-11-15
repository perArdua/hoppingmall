package com.hoppingmall.product.wishlist.dto.response

data class WishlistCheckResponse(
    val wishlisted: Boolean,
    val wishlistId: Long?
) {
    companion object {
        fun of(wishlistId: Long?): WishlistCheckResponse =
            WishlistCheckResponse(wishlisted = wishlistId != null, wishlistId = wishlistId)
    }
}
