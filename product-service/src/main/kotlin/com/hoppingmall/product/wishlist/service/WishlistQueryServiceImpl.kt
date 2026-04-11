package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class WishlistQueryServiceImpl(
    private val wishlistRepository: WishlistRepository,
    private val productRepository: ProductRepository
) : WishlistQueryService {

    override fun getWishlists(buyerId: Long, pageable: Pageable): Slice<WishlistResponse> {
        val wishlists = wishlistRepository.findByBuyerId(buyerId, pageable)
        val productIds = wishlists.content.map { it.productId }
        val productMap = productRepository.findAllById(productIds).associateBy { it.id!! }

        return wishlists.map { WishlistResponse.from(it, productMap[it.productId]) }
    }

    override fun isWishlisted(buyerId: Long, productId: Long): Boolean {
        return wishlistRepository.existsByBuyerIdAndProductId(buyerId, productId)
    }
}
