package com.hoppingmall.mall.wishlist.service

import com.hoppingmall.mall.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.mall.wishlist.dto.response.WishlistResponse

interface WishlistCommandService {
    fun addWishlist(buyerId: Long, request: WishlistCreateRequest): WishlistResponse
    fun removeWishlist(buyerId: Long, wishlistId: Long)
}
