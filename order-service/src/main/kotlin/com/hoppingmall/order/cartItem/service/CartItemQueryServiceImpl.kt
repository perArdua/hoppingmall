package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CartItemQueryServiceImpl(
    private val cartItemRepository: CartItemRepository
) : CartItemQueryService {

    override fun getCartItems(buyerId: Long, pageable: Pageable): Slice<CartItemResponse> {
        return cartItemRepository.findByBuyerId(buyerId, pageable)
            .map { CartItemResponse.from(it) }
    }
}
