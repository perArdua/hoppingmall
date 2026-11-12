package com.hoppingmall.order.cartItem.domain.repository

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.dto.CartAggregation
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CartItemRepository: JpaRepository<CartItem, Long> {
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Slice<CartItem>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CartItem c WHERE c.buyerId = :buyerId AND c.productId = :productId AND c.deletedAt IS NULL")
    fun findByBuyerIdAndProductId(@Param("buyerId") buyerId: Long, @Param("productId") productId: Long): CartItem?

    @Modifying
    @Query(
        value = "DELETE FROM cart_items WHERE buyer_id = :buyerId AND product_id = :productId AND deleted_at IS NOT NULL",
        nativeQuery = true
    )
    fun deleteSoftDeleted(@Param("buyerId") buyerId: Long, @Param("productId") productId: Long): Int

    @Query(
        value = """
            SELECT ci.product_id AS productId,
                   COUNT(DISTINCT ci.buyer_id) AS buyerCount
            FROM cart_items ci
            WHERE ci.deleted_at IS NULL
            GROUP BY ci.product_id
        """,
        nativeQuery = true
    )
    fun aggregateCartByProduct(): List<CartAggregation>
}
