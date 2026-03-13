package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.domain.CartItem
import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.mall.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import com.hoppingmall.mall.cartItem.exception.CartItemAccessDeniedException
import com.hoppingmall.mall.cartItem.exception.CartItemNotFoundException
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.ProductImage
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CartItemCommandServiceImpl(
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository
) : CartItemCommandService {

    override fun addCartItem(buyerId: Long, request: CartItemCreateRequest): CartItemResponse {
        val product = productRepository.findById(request.productId)
            .orElseThrow { ProductNotFoundException() }
        
        val productImage = productImageRepository.findByProductIdOrderBySortOrder(request.productId).firstOrNull()
        val existingCartItem = cartItemRepository.findByBuyerIdAndProductId(buyerId, request.productId)

        val cartItem = if (existingCartItem != null) {
            updateExistingCartItem(existingCartItem, request.quantity)
        } else {
            createNewCartItem(buyerId, product, productImage, request.quantity)
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
        product: Product, 
        productImage: ProductImage?, 
        quantity: Int
    ): CartItem {
        val newCartItem = CartItem.create(
            buyerId = buyerId,
            productId = product.id!!,
            productName = product.name,
            productPrice = product.price,
            productImageUrl = productImage?.imageUrl,
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