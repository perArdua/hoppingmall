package com.hoppingmall.product.wishlist.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.dto.response.WishlistResponse
import com.hoppingmall.product.wishlist.service.WishlistCommandService
import com.hoppingmall.product.wishlist.service.WishlistQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.hoppingmall.product.common.enums.ProductStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("WishlistController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class WishlistControllerTest {

    @Mock
    private lateinit var wishlistCommandService: WishlistCommandService

    @Mock
    private lateinit var wishlistQueryService: WishlistQueryService

    @InjectMocks
    private lateinit var controller: WishlistController

    private val principal = UserPrincipal(1L, "BUYER")

    private fun wishlistResponse() = WishlistResponse(
        id = 1L, productId = 10L,
        productName = "테스트 상품", productPrice = BigDecimal("10000"),
        productStatus = ProductStatus.AVAILABLE, createdAt = LocalDateTime.now()
    )

    @Test
    fun 위시리스트에_추가한다() {
        val request = WishlistCreateRequest(productId = 10L)

        whenever(wishlistCommandService.addWishlist(eq(1L), any())).thenReturn(wishlistResponse())

        val result = controller.addWishlist(principal, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
    }

    @Test
    fun 위시리스트에서_제거한다() {
        val result = controller.removeWishlist(principal, 1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(wishlistCommandService).removeWishlist(1L, 1L)
    }

    @Test
    fun 위시리스트_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)

        whenever(wishlistQueryService.getWishlists(1L, pageable))
            .thenReturn(SliceImpl(listOf(wishlistResponse()), pageable, false))

        val result = controller.getWishlists(principal, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 위시리스트_여부를_확인한다() {
        whenever(wishlistQueryService.isWishlisted(1L, 10L)).thenReturn(true)

        val result = controller.isWishlisted(principal, 10L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data).isTrue()
    }
}
