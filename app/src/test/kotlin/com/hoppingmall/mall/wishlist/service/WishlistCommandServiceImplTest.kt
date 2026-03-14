package com.hoppingmall.mall.wishlist.service

import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.wishlist.domain.Wishlist
import com.hoppingmall.mall.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.mall.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.mall.wishlist.exception.WishlistAlreadyExistsException
import com.hoppingmall.mall.wishlist.exception.WishlistNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@DisplayName("WishlistCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class WishlistCommandServiceImplTest {

    @Mock
    private lateinit var wishlistRepository: WishlistRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    private lateinit var wishlistCommandService: WishlistCommandServiceImpl

    @BeforeEach
    fun setUp() {
        wishlistCommandService = WishlistCommandServiceImpl(wishlistRepository, productRepository)
    }

    @Nested
    @DisplayName("addWishlist")
    inner class AddWishlist {

        @Test
        fun 찜_추가_성공() {
            // Data
            val buyerId = 1L
            val request = WishlistCreateRequest(productId = 100L)
            val product = Product.fixture().withId(100L)

            // Context
            whenever(productRepository.findById(request.productId)).thenReturn(java.util.Optional.of(product))
            whenever(wishlistRepository.existsByBuyerIdAndProductId(buyerId, request.productId)).thenReturn(false)
            whenever(wishlistRepository.save(any<Wishlist>())).thenAnswer { invocation ->
                val wishlist = invocation.getArgument<Wishlist>(0)
                wishlist.withId(1L)
            }

            // Interaction
            val result = wishlistCommandService.addWishlist(buyerId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.productId).isEqualTo(request.productId)

            verify(wishlistRepository).save(any())
        }

        @Test
        fun 존재하지_않는_상품_찜_추가_시_예외_발생() {
            // Data
            val buyerId = 1L
            val request = WishlistCreateRequest(productId = 999L)

            // Context
            whenever(productRepository.findById(request.productId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { wishlistCommandService.addWishlist(buyerId, request) }
                .isInstanceOf(ProductNotFoundException::class.java)
        }

        @Test
        fun 이미_찜한_상품_추가_시_예외_발생() {
            // Data
            val buyerId = 1L
            val request = WishlistCreateRequest(productId = 100L)
            val product = Product.fixture().withId(100L)

            // Context
            whenever(productRepository.findById(request.productId)).thenReturn(java.util.Optional.of(product))
            whenever(wishlistRepository.existsByBuyerIdAndProductId(buyerId, request.productId)).thenReturn(true)

            // Interaction & Assertions
            assertThatThrownBy { wishlistCommandService.addWishlist(buyerId, request) }
                .isInstanceOf(WishlistAlreadyExistsException::class.java)
        }
    }

    @Nested
    @DisplayName("removeWishlist")
    inner class RemoveWishlist {

        @Test
        fun 찜_삭제_성공() {
            // Data
            val buyerId = 1L
            val wishlistId = 1L
            val wishlist = Wishlist.fixture(buyerId = buyerId, productId = 100L).withId(wishlistId)

            // Context
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(java.util.Optional.of(wishlist))

            // Interaction
            wishlistCommandService.removeWishlist(buyerId, wishlistId)

            // Assertions
            assertThat(wishlist.deletedAt).isNotNull()
        }

        @Test
        fun 존재하지_않는_찜_삭제_시_예외_발생() {
            // Data
            val buyerId = 1L
            val wishlistId = 999L

            // Context
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { wishlistCommandService.removeWishlist(buyerId, wishlistId) }
                .isInstanceOf(WishlistNotFoundException::class.java)
        }

        @Test
        fun 다른_사용자의_찜_삭제_시_예외_발생() {
            // Data
            val buyerId = 1L
            val wishlistId = 1L
            val wishlist = Wishlist.fixture(buyerId = 2L, productId = 100L).withId(wishlistId)

            // Context
            whenever(wishlistRepository.findById(wishlistId)).thenReturn(java.util.Optional.of(wishlist))

            // Interaction & Assertions
            assertThatThrownBy { wishlistCommandService.removeWishlist(buyerId, wishlistId) }
                .isInstanceOf(WishlistNotFoundException::class.java)
        }
    }
}
