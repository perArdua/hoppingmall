package com.hoppingmall.order.cartItem.service

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

        val productImageUrl = productQueryPort.findProductImageUrl(request.productId)
        val existingCartItem = cartItemRepository.findByBuyerIdAndProductId(buyerId, request.productId)

        val cartItem = if (existingCartItem != null) {
            updateExistingCartItem(existingCartItem, request.quantity)
        } else {
            createNewCartItem(buyerId, product, productImageUrl, request.quantity)
        }

        return CartItemResponse.from(cartItem)
    }

    private fun updateExistingCartItem(existingCartItem: CartItem, additionalQuantity: Int): CartItem {
        val updatedQuantity = existingCartItem.quantity + additionalQuantity
        val updatedCartItem = CartItem.create(
            buyerId = existingCartItem.buyerId,
            productId = existingCartItem.productId,
            productName = existingCartItem.productName,
            productPrice = existingCartItem.productPrice,
            productImageUrl = existingCartItem.productImageUrl,
            quantity = updatedQuantity
        )
        return cartItemRepository.save(updatedCartItem)
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
        val cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow { CartItemNotFoundException() }

        if (cartItem.buyerId != buyerId) {
            throw CartItemAccessDeniedException()
        }

        val updatedCartItem = CartItem.create(
            buyerId = cartItem.buyerId,
            productId = cartItem.productId,
            productName = cartItem.productName,
            productPrice = cartItem.productPrice,
            productImageUrl = cartItem.productImageUrl,
            quantity = request.quantity
        )
        val savedCartItem = cartItemRepository.save(updatedCartItem)

        return CartItemResponse.from(savedCartItem)
    }

    override fun removeCartItem(buyerId: Long, cartItemId: Long) {
        val cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow { CartItemNotFoundException() }

        if (cartItem.buyerId != buyerId) {
            throw CartItemAccessDeniedException()
        }

        cartItem.softDelete()
    }
}
