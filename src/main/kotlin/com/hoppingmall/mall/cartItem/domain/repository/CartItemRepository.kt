package com.hoppingmall.mall.cartItem.domain.repository

import com.hoppingmall.mall.cartItem.domain.CartItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CartItemRepository: JpaRepository<CartItem, Long> {
    fun findByBuyerId(buyerId: Long): List<CartItem>
    fun findByBuyerIdAndProductId(buyerId: Long, productId: Long): CartItem?
}