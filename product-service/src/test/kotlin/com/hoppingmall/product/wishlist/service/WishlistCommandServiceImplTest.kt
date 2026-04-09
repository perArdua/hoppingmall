package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductNotFoundException
import com.hoppingmall.product.support.withId
import com.hoppingmall.product.wishlist.domain.Wishlist
import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.exception.WishlistAlreadyExistsException
import com.hoppingmall.product.wishlist.exception.WishlistNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional

@DisplayName("WishlistCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class WishlistCommandServiceImplTest {

    @Mock
    private lateinit var wishlistRepository: WishlistRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @InjectMocks
    private lateinit var service: WishlistCommandServiceImpl

    @Test
    fun 위시리스트에_추가한다() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(10L)
        val wishlist = Wishlist.create(buyerId = 1L, productId = 10L).withId(1L)
        val request = WishlistCreateRequest(productId = 10L)

        whenever(productRepository.findById(10L)).thenReturn(Optional.of(product))
        whenever(wishlistRepository.existsByBuyerIdAndProductId(1L, 10L)).thenReturn(false)
        whenever(wishlistRepository.save(any<Wishlist>())).thenReturn(wishlist)

        val result = service.addWishlist(1L, request)

        assertThat(result.productId).isEqualTo(10L)
    }

    @Test
    fun 존재하지_않는_상품을_위시리스트에_추가_시_예외를_발생시킨다() {
        val request = WishlistCreateRequest(productId = 999L)

        whenever(productRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.addWishlist(1L, request) }
            .isInstanceOf(ProductNotFoundException::class.java)
    }

    @Test
    fun 이미_위시리스트에_있는_상품_추가_시_예외를_발생시킨다() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(10L)
        val request = WishlistCreateRequest(productId = 10L)

        whenever(productRepository.findById(10L)).thenReturn(Optional.of(product))
        whenever(wishlistRepository.existsByBuyerIdAndProductId(1L, 10L)).thenReturn(true)

        assertThatThrownBy { service.addWishlist(1L, request) }
            .isInstanceOf(WishlistAlreadyExistsException::class.java)
    }

    @Test
    fun 위시리스트에서_제거한다() {
        val wishlist = Wishlist.create(buyerId = 1L, productId = 10L).withId(1L)

        whenever(wishlistRepository.findById(1L)).thenReturn(Optional.of(wishlist))

        service.removeWishlist(1L, 1L)

        assertThat(wishlist.deletedAt).isNotNull()
    }

    @Test
    fun 존재하지_않는_위시리스트_제거_시_예외를_발생시킨다() {
        whenever(wishlistRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.removeWishlist(1L, 999L) }
            .isInstanceOf(WishlistNotFoundException::class.java)
    }

    @Test
    fun 다른_사용자의_위시리스트_제거_시_예외를_발생시킨다() {
        val wishlist = Wishlist.create(buyerId = 1L, productId = 10L).withId(1L)

        whenever(wishlistRepository.findById(1L)).thenReturn(Optional.of(wishlist))

        assertThatThrownBy { service.removeWishlist(999L, 1L) }
            .isInstanceOf(WishlistNotFoundException::class.java)
    }
}
