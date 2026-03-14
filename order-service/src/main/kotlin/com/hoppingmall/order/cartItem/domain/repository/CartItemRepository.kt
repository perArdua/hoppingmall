package com.hoppingmall.order.cartItem.domain.repository

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.dto.CartAggregation
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CartItemRepository: JpaRepository<CartItem, Long> {
    fun findByBuyerId(buyerId: Long, pageable: Pageable): Slice<CartItem>
    fun findByBuyerIdAndProductId(buyerId: Long, productId: Long): CartItem?

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
