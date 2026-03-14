package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface CartItemQueryService {
    fun getCartItems(buyerId: Long, pageable: Pageable): Slice<CartItemResponse>
}
