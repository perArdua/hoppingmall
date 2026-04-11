package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
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
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("WishlistQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class WishlistQueryServiceImplTest {

    @Mock
    private lateinit var wishlistRepository: WishlistRepository

    @InjectMocks
    private lateinit var service: WishlistQueryServiceImpl

    @Test
    fun 위시리스트를_조회하면_상품_정보가_포함된다() {
        val pageable = PageRequest.of(0, 20)
        val response = WishlistResponse(
            id = 1L, productId = 10L,
            productName = "테스트 상품", productPrice = BigDecimal("10000"),
            productStatus = ProductStatus.AVAILABLE, createdAt = LocalDateTime.now()
        )

        whenever(wishlistRepository.findByBuyerIdWithProduct(1L, pageable))
            .thenReturn(SliceImpl(listOf(response), pageable, false))

        val result = service.getWishlists(1L, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].productName).isEqualTo("테스트 상품")
        assertThat(result.content[0].productPrice).isEqualByComparingTo(BigDecimal("10000"))
    }

    @Test
    fun 위시리스트를_조회하면_삭제된_상품은_null로_반환된다() {
        val pageable = PageRequest.of(0, 20)
        val response = WishlistResponse(
            id = 1L, productId = 10L,
            productName = null, productPrice = null,
            productStatus = null, createdAt = LocalDateTime.now()
        )

        whenever(wishlistRepository.findByBuyerIdWithProduct(1L, pageable))
            .thenReturn(SliceImpl(listOf(response), pageable, false))

        val result = service.getWishlists(1L, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].productName).isNull()
        assertThat(result.content[0].productPrice).isNull()
        assertThat(result.content[0].productStatus).isNull()
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
