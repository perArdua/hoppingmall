package com.hoppingmall.mall.wishlist.service

import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.wishlist.domain.Wishlist
import com.hoppingmall.mall.wishlist.domain.repository.WishlistRepository
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@DisplayName("WishlistQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class WishlistQueryServiceImplTest {

    @Mock
    private lateinit var wishlistRepository: WishlistRepository

    private lateinit var wishlistQueryService: WishlistQueryServiceImpl

    @BeforeEach
    fun setUp() {
        wishlistQueryService = WishlistQueryServiceImpl(wishlistRepository)
    }

    @Nested
    @DisplayName("getWishlists")
    inner class GetWishlists {

        @Test
        fun 빈_찜_목록_조회_성공() {
            // Data
            val buyerId = 1L
            val pageable = PageRequest.of(0, 20)

            // Context
            whenever(wishlistRepository.findByBuyerId(buyerId, pageable))
                .thenReturn(PageImpl(emptyList(), pageable, 0))

            // Interaction
            val result = wishlistQueryService.getWishlists(buyerId, pageable)

            // Assertions
            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)

            verify(wishlistRepository).findByBuyerId(buyerId, pageable)
        }

        @Test
        fun 찜_목록_조회_성공() {
            // Data
            val buyerId = 1L
            val pageable = PageRequest.of(0, 20)
            val wishlists = listOf(
                Wishlist.fixture(buyerId = buyerId, productId = 100L).withId(1L),
                Wishlist.fixture(buyerId = buyerId, productId = 200L).withId(2L)
            )

            // Context
            whenever(wishlistRepository.findByBuyerId(buyerId, pageable))
                .thenReturn(PageImpl(wishlists, pageable, 2))

            // Interaction
            val result = wishlistQueryService.getWishlists(buyerId, pageable)

            // Assertions
            assertThat(result.content).hasSize(2)
            assertThat(result.content[0].productId).isEqualTo(100L)
            assertThat(result.content[1].productId).isEqualTo(200L)
            assertThat(result.totalElements).isEqualTo(2)

            verify(wishlistRepository).findByBuyerId(buyerId, pageable)
        }
    }

    @Nested
    @DisplayName("isWishlisted")
    inner class IsWishlisted {

        @Test
        fun 찜한_상품_확인_true() {
            // Data
            val buyerId = 1L
            val productId = 100L

            // Context
            whenever(wishlistRepository.existsByBuyerIdAndProductId(buyerId, productId)).thenReturn(true)

            // Interaction
            val result = wishlistQueryService.isWishlisted(buyerId, productId)

            // Assertions
            assertThat(result).isTrue()

            verify(wishlistRepository).existsByBuyerIdAndProductId(buyerId, productId)
        }

        @Test
        fun 찜하지_않은_상품_확인_false() {
            // Data
            val buyerId = 1L
            val productId = 100L

            // Context
            whenever(wishlistRepository.existsByBuyerIdAndProductId(buyerId, productId)).thenReturn(false)

            // Interaction
            val result = wishlistQueryService.isWishlisted(buyerId, productId)

            // Assertions
            assertThat(result).isFalse()

            verify(wishlistRepository).existsByBuyerIdAndProductId(buyerId, productId)
        }
    }
}
