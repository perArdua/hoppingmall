package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
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
import java.math.BigDecimal

@DisplayName("WishlistQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class WishlistQueryServiceImplTest {

    @Mock
    private lateinit var wishlistRepository: WishlistRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @InjectMocks
    private lateinit var service: WishlistQueryServiceImpl

    @Test
    fun 위시리스트를_조회하면_상품_정보가_포함된다() {
        val pageable = PageRequest.of(0, 20)
        val wishlist = Wishlist.create(buyerId = 1L, productId = 10L).withId(1L)
        val product = Product.create(
            sellerId = 2L, categoryId = 1L, name = "테스트 상품",
            description = "설명", price = BigDecimal("10000"),
            status = ProductStatus.AVAILABLE
        ).withId(10L)

        whenever(wishlistRepository.findByBuyerId(1L, pageable))
            .thenReturn(SliceImpl(listOf(wishlist), pageable, false))
        whenever(productRepository.findAllById(listOf(10L)))
            .thenReturn(listOf(product))

        val result = service.getWishlists(1L, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].productName).isEqualTo("테스트 상품")
        assertThat(result.content[0].productPrice).isEqualByComparingTo(BigDecimal("10000"))
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
