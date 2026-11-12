package com.hoppingmall.order.cartItem.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.cartItem.exception.CartItemAccessDeniedException
import com.hoppingmall.order.cartItem.exception.CartItemConcurrentAddException
import com.hoppingmall.order.cartItem.exception.CartItemInvalidQuantityException
import com.hoppingmall.order.cartItem.exception.CartItemNotFoundException
import com.hoppingmall.order.cartItem.exception.CartItemProductNotFoundException
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
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
        existingCartItem.updateQuantity(validQuantity(existingCartItem.quantity.toLong() + additionalQuantity))
        return existingCartItem
    }

    private fun createNewCartItem(
        buyerId: Long,
        product: ProductInfo,
        productImageUrl: String?,
        quantity: Int
    ): CartItem {
        validQuantity(quantity.toLong())
        val newCartItem = CartItem.create(
            buyerId = buyerId,
            productId = product.id,
            productName = product.name,
            productPrice = product.price,
            productImageUrl = productImageUrl,
            quantity = quantity
        )
        cartItemRepository.deleteSoftDeleted(buyerId, product.id)
        return try {
            cartItemRepository.save(newCartItem)
        } catch (e: DataIntegrityViolationException) {
            throw CartItemConcurrentAddException()
        } catch (e: PessimisticLockingFailureException) {
            throw CartItemConcurrentAddException()
        }
    }

    override fun updateCartItemQuantity(buyerId: Long, cartItemId: Long, request: CartItemUpdateRequest): CartItemResponse {
        val cartItem = cartItemRepository.findByIdOrNull(cartItemId) ?: throw CartItemNotFoundException() 

        if (cartItem.buyerId != buyerId) {
            throw CartItemAccessDeniedException()
        }

        cartItem.updateQuantity(validQuantity(request.quantity.toLong()))

        return CartItemResponse.from(cartItem)
    }

    override fun removeCartItem(buyerId: Long, cartItemId: Long) {
        val cartItem = cartItemRepository.findByIdOrNull(cartItemId) ?: throw CartItemNotFoundException() 

        if (cartItem.buyerId != buyerId) {
            throw CartItemAccessDeniedException()
        }

        cartItem.softDelete()
    }

    private fun validQuantity(quantity: Long): Int {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw CartItemInvalidQuantityException()
        }
        return quantity.toInt()
    }

    companion object {
        private const val MIN_QUANTITY = 1L
        private const val MAX_QUANTITY = 999_999_999L
    }
}
