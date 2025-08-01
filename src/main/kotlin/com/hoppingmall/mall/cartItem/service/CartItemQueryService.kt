package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse

interface CartItemQueryService {
    fun getCartItems(buyerId: Long): List<CartItemResponse>
} 