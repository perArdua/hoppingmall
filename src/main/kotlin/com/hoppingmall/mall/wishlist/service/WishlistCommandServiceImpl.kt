package com.hoppingmall.mall.wishlist.service

import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import com.hoppingmall.mall.wishlist.domain.Wishlist
import com.hoppingmall.mall.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.mall.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.mall.wishlist.dto.response.WishlistResponse
import com.hoppingmall.mall.wishlist.exception.WishlistAlreadyExistsException
import com.hoppingmall.mall.wishlist.exception.WishlistNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WishlistCommandServiceImpl(
    private val wishlistRepository: WishlistRepository,
    private val productRepository: ProductRepository
) : WishlistCommandService {

    override fun addWishlist(buyerId: Long, request: WishlistCreateRequest): WishlistResponse {
        productRepository.findById(request.productId)
            .orElseThrow { ProductNotFoundException() }

        if (wishlistRepository.existsByBuyerIdAndProductId(buyerId, request.productId)) {
            throw WishlistAlreadyExistsException()
        }

        val wishlist = Wishlist.create(buyerId, request.productId)
        val savedWishlist = wishlistRepository.save(wishlist)

        return WishlistResponse.from(savedWishlist)
    }

    override fun removeWishlist(buyerId: Long, wishlistId: Long) {
        val wishlist = wishlistRepository.findById(wishlistId)
            .orElseThrow { WishlistNotFoundException() }

        if (wishlist.buyerId != buyerId) {
            throw WishlistNotFoundException()
        }

        wishlist.softDelete()
    }
}
