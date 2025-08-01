package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CartItemQueryServiceImpl(
    private val cartItemRepository: CartItemRepository
) : CartItemQueryService {

    override fun getCartItems(buyerId: Long): List<CartItemResponse> {
        val cartItems = cartItemRepository.findByBuyerId(buyerId)

        return cartItems.map { CartItemResponse.from(it) }
    }
}