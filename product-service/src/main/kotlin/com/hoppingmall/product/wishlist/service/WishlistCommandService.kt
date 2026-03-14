package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse

interface WishlistCommandService {
    fun addWishlist(buyerId: Long, request: WishlistCreateRequest): WishlistResponse
    fun removeWishlist(buyerId: Long, wishlistId: Long)
}
