package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class WishlistQueryServiceImpl(
    private val wishlistRepository: WishlistRepository
) : WishlistQueryService {

    override fun getWishlists(buyerId: Long, pageable: Pageable): Page<WishlistResponse> {
        return wishlistRepository.findByBuyerId(buyerId, pageable)
            .map { WishlistResponse.from(it) }
    }

    override fun isWishlisted(buyerId: Long, productId: Long): Boolean {
        return wishlistRepository.existsByBuyerIdAndProductId(buyerId, productId)
    }
}
