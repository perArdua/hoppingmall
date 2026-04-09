package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.support.withId
import com.hoppingmall.product.wishlist.domain.Wishlist
import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl

@DisplayName("WishlistQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class WishlistQueryServiceImplTest {

    @Mock
    private lateinit var wishlistRepository: WishlistRepository

    @InjectMocks
    private lateinit var service: WishlistQueryServiceImpl

    @Test
    fun 위시리스트를_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val wishlist = Wishlist.create(buyerId = 1L, productId = 10L).withId(1L)

        whenever(wishlistRepository.findByBuyerId(1L, pageable))
            .thenReturn(SliceImpl(listOf(wishlist), pageable, false))

        val result = service.getWishlists(1L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 위시리스트_여부를_확인한다_존재하는_경우() {
        whenever(wishlistRepository.existsByBuyerIdAndProductId(1L, 10L)).thenReturn(true)

        val result = service.isWishlisted(1L, 10L)

        assertThat(result).isTrue()
    }

    @Test
    fun 위시리스트_여부를_확인한다_존재하지_않는_경우() {
        whenever(wishlistRepository.existsByBuyerIdAndProductId(1L, 10L)).thenReturn(false)

        val result = service.isWishlisted(1L, 10L)

        assertThat(result).isFalse()
    }
}
