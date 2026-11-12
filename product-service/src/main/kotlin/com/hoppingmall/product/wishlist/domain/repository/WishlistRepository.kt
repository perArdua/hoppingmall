package com.hoppingmall.product.wishlist.domain.repository

import com.hoppingmall.product.wishlist.domain.Wishlist
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface WishlistRepository : JpaRepository<Wishlist, Long> {

    @Query("""
        SELECT new com.hoppingmall.product.wishlist.dto.response.WishlistResponse(
            w.id, w.productId, p.name, p.price, p.status, w.createdAt
        )
        FROM Wishlist w JOIN Product p ON w.productId = p.id
        WHERE w.buyerId = :buyerId
    """)
    fun findByBuyerIdWithProduct(buyerId: Long, pageable: Pageable): Slice<WishlistResponse>

    fun existsByBuyerIdAndProductId(buyerId: Long, productId: Long): Boolean

    @Modifying
    @Query(
        value = "DELETE FROM wishlists WHERE buyer_id = :buyerId AND product_id = :productId AND deleted_at IS NOT NULL",
        nativeQuery = true
    )
    fun deleteSoftDeleted(@Param("buyerId") buyerId: Long, @Param("productId") productId: Long): Int
}
