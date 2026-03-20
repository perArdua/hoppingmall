package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface WishlistQueryService {
    fun getWishlists(buyerId: Long, pageable: Pageable): Slice<WishlistResponse>
    fun isWishlisted(buyerId: Long, productId: Long): Boolean
}
