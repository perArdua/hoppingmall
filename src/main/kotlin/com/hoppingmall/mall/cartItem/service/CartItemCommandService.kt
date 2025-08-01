package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.mall.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse

interface CartItemCommandService {
    fun addCartItem(buyerId: Long, request: CartItemCreateRequest): CartItemResponse
    fun updateCartItemQuantity(buyerId: Long, cartItemId: Long, request: CartItemUpdateRequest): CartItemResponse
    fun removeCartItem(buyerId: Long, cartItemId: Long)
} 