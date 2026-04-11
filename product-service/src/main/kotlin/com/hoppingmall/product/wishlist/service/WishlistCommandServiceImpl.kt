package com.hoppingmall.product.wishlist.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductNotFoundException
import com.hoppingmall.product.wishlist.domain.Wishlist
import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import com.hoppingmall.product.wishlist.exception.WishlistAlreadyExistsException
import com.hoppingmall.product.wishlist.exception.WishlistNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WishlistCommandServiceImpl(
    private val wishlistRepository: WishlistRepository,
    private val productRepository: ProductRepository
) : WishlistCommandService {

    override fun addWishlist(buyerId: Long, request: WishlistCreateRequest): WishlistResponse {
        val product = productRepository.findByIdOrNull(request.productId) ?: throw ProductNotFoundException()

        if (wishlistRepository.existsByBuyerIdAndProductId(buyerId, request.productId)) {
            throw WishlistAlreadyExistsException()
        }

        val wishlist = Wishlist.create(buyerId, request.productId)
        val savedWishlist = wishlistRepository.save(wishlist)

        return WishlistResponse.from(savedWishlist, product)
    }

    override fun removeWishlist(buyerId: Long, wishlistId: Long) {
        val wishlist = wishlistRepository.findByIdOrNull(wishlistId) ?: throw WishlistNotFoundException() 

        if (wishlist.buyerId != buyerId) {
            throw WishlistNotFoundException()
        }

        wishlist.softDelete()
    }
}
