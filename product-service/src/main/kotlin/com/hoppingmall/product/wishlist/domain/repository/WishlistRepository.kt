package com.hoppingmall.product.wishlist.domain.repository

import com.hoppingmall.product.wishlist.domain.Wishlist
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface WishlistRepository : JpaRepository<Wishlist, Long> {

    @Query("""
        SELECT new com.hoppingmall.product.wishlist.dto.response.WishlistResponse(
            w.id, w.productId, p.name, p.price, p.status, w.createdAt
        )
        FROM Wishlist w LEFT JOIN Product p ON w.productId = p.id
        WHERE w.buyerId = :buyerId
    """)
    fun findByBuyerIdWithProduct(buyerId: Long, pageable: Pageable): Slice<WishlistResponse>

    fun existsByBuyerIdAndProductId(buyerId: Long, productId: Long): Boolean
}
