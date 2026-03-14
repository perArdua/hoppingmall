package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse

interface CartItemCommandService {
    fun addCartItem(buyerId: Long, request: CartItemCreateRequest): CartItemResponse
    fun updateCartItemQuantity(buyerId: Long, cartItemId: Long, request: CartItemUpdateRequest): CartItemResponse
    fun removeCartItem(buyerId: Long, cartItemId: Long)
}
