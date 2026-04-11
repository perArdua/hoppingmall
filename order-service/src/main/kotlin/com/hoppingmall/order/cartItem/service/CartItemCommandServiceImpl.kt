package com.hoppingmall.order.cartItem.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.cartItem.exception.CartItemAccessDeniedException
import com.hoppingmall.order.cartItem.exception.CartItemNotFoundException
import com.hoppingmall.order.cartItem.exception.CartItemProductNotFoundException
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CartItemCommandServiceImpl(
    private val cartItemRepository: CartItemRepository,
    private val productQueryPort: ProductQueryPort
) : CartItemCommandService {

    override fun addCartItem(buyerId: Long, request: CartItemCreateRequest): CartItemResponse {
        val product = productQueryPort.findProductById(request.productId)
            ?: throw CartItemProductNotFoundException()

        val existingCartItem = cartItemRepository.findByBuyerIdAndProductId(buyerId, request.productId)

        val cartItem = if (existingCartItem != null) {
            updateExistingCartItem(existingCartItem, request.quantity)
        } else {
            createNewCartItem(buyerId, product, product.imageUrl, request.quantity)
        }

        return CartItemResponse.from(cartItem)
    }

    private fun updateExistingCartItem(existingCartItem: CartItem, additionalQuantity: Int): CartItem {
        existingCartItem.updateQuantity(existingCartItem.quantity + additionalQuantity)
        return existingCartItem
    }

    private fun createNewCartItem(
        buyerId: Long,
        product: ProductInfo,
        productImageUrl: String?,
        quantity: Int
    ): CartItem {
        val newCartItem = CartItem.create(
            buyerId = buyerId,
            productId = product.id,
            productName = product.name,
            productPrice = product.price,
            productImageUrl = productImageUrl,
            quantity = quantity
        )
        return cartItemRepository.save(newCartItem)
    }

    override fun updateCartItemQuantity(buyerId: Long, cartItemId: Long, request: CartItemUpdateRequest): CartItemResponse {
        val cartItem = cartItemRepository.findByIdOrNull(cartItemId) ?: throw CartItemNotFoundException() 

        if (cartItem.buyerId != buyerId) {
            throw CartItemAccessDeniedException()
        }

        cartItem.updateQuantity(request.quantity)

        return CartItemResponse.from(cartItem)
    }

    override fun removeCartItem(buyerId: Long, cartItemId: Long) {
        val cartItem = cartItemRepository.findByIdOrNull(cartItemId) ?: throw CartItemNotFoundException() 

        if (cartItem.buyerId != buyerId) {
            throw CartItemAccessDeniedException()
        }

        cartItem.softDelete()
    }
}
