package com.hoppingmall.product.wishlist.domain.repository

import com.hoppingmall.product.wishlist.domain.Wishlist
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WishlistRepository : JpaRepository<Wishlist, Long> {
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Page<Wishlist>
    fun existsByBuyerIdAndProductId(buyerId: Long, productId: Long): Boolean
}
