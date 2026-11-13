package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
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
        val sellingProductIds = sellingProductIds(cartItems.content.map { it.productId }.distinct())
        val responses = cartItems.content
            .filter { sellingProductIds == null || it.productId in sellingProductIds }
            .map { CartItemResponse.from(it) }
        return SliceImpl(responses, cartItems.pageable, cartItems.hasNext())
    }

    private fun sellingProductIds(productIds: List<Long>): Set<Long>? {
        val port = productQueryPort ?: return null
        if (productIds.isEmpty()) return emptySet()
        return try {
            port.findProductsByIds(productIds).map { it.id }.toSet()
        } catch (e: ProductQueryUnavailableException) {
            log.warn("장바구니 목록의 상품 조회 실패, 삭제된 상품을 빼지 않고 돌려줌: productIds={}", productIds, e)
            null
        }
    }
}
