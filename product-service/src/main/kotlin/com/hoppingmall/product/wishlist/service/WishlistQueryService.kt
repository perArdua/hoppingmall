package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface WishlistQueryService {
    fun getWishlists(buyerId: Long, pageable: Pageable): Page<WishlistResponse>
    fun isWishlisted(buyerId: Long, productId: Long): Boolean
}
