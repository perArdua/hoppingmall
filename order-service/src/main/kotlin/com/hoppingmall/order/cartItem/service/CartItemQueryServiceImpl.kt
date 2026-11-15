package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CartItemQueryServiceImpl(
    private val cartItemRepository: CartItemRepository
) : CartItemQueryService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired(required = false)
    var productQueryPort: ProductQueryPort? = null

    override fun getCartItems(buyerId: Long, pageable: Pageable): Slice<CartItemResponse> {
        val cartItems = cartItemRepository.findByBuyerId(buyerId, pageable)
        val products = currentProducts(cartItems.content.map { it.productId }.distinct())
        return cartItems.map { cartItem -> response(cartItem, products) }
    }

    private fun response(cartItem: CartItem, products: Map<Long, ProductInfo>?): CartItemResponse {
        if (products == null) return CartItemResponse.from(cartItem)
        val product = products[cartItem.productId] ?: return CartItemResponse.deleted(cartItem)
        return CartItemResponse.of(cartItem, product)
    }

    private fun currentProducts(productIds: List<Long>): Map<Long, ProductInfo>? {
        val port = productQueryPort ?: return null
        if (productIds.isEmpty()) return emptyMap()
        return try {
            port.findProductsByIds(productIds).associateBy { it.id }
        } catch (e: ProductQueryUnavailableException) {
            log.warn("장바구니 목록의 상품 조회 실패, 담을 때 값으로 돌려줌: productIds={}", productIds, e)
            null
        }
    }
}
