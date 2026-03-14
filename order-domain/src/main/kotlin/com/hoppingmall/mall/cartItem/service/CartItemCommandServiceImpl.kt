package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.domain.CartItem
import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.mall.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import com.hoppingmall.mall.cartItem.exception.CartItemAccessDeniedException
import com.hoppingmall.mall.cartItem.exception.CartItemNotFoundException
import com.hoppingmall.mall.order.exception.OrderProductNotFoundException
import com.hoppingmall.mall.product.api.ProductQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CartItemCommandServiceImpl(
    private val cartItemRepository: CartItemRepository,
    private val productQueryPort: ProductQueryPort
) : CartItemCommandService {

    override fun addCartItem(buyerId: Long, request: CartItemCreateRequest): CartItemResponse {
        val product = productQueryPort.findById(request.productId)
            ?: throw OrderProductNotFoundException()

        val productImageUrl = productQueryPort.findMainImageUrl(request.productId)
        val existingCartItem = cartItemRepository.findByBuyerIdAndProductId(buyerId, request.productId)

        val cartItem = if (existingCartItem != null) {
            updateExistingCartItem(existingCartItem, request.quantity)
        } else {
            createNewCartItem(buyerId, product.id, product.name, product.price, productImageUrl, request.quantity)
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
        productId: Long,
        productName: String,
        productPrice: java.math.BigDecimal,
        productImageUrl: String?,
        quantity: Int
    ): CartItem {
        val newCartItem = CartItem.create(
            buyerId = buyerId,
            productId = productId,
            productName = productName,
            productPrice = productPrice,
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