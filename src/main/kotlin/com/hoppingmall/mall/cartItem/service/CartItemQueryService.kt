package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface CartItemQueryService {
    fun getCartItems(buyerId: Long, pageable: Pageable): Slice<CartItemResponse>
} 