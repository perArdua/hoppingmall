package com.hoppingmall.order.cartItem.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.dto.response.CartItemResponse
import com.hoppingmall.order.cartItem.service.CartItemCommandService
import com.hoppingmall.order.cartItem.service.CartItemQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@DisplayName("CartItemController")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemControllerTest {

    @Mock
    private lateinit var cartItemCommandService: CartItemCommandService

    @Mock
    private lateinit var cartItemQueryService: CartItemQueryService

    @InjectMocks
    private lateinit var controller: CartItemController

    private val userPrincipal = UserPrincipal.of(1L, "BUYER")

    private fun createCartItemResponse(id: Long = 1L): CartItemResponse {
        return CartItemResponse(
            id = id,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("10000"),
            productImageUrl = null,
            quantity = 2,
            totalPrice = BigDecimal("20000")
        )
    }

    @Test
    fun 장바구니에_상품을_추가한다() {
        val request = CartItemCreateRequest(productId = 100L, quantity = 2)
        val response = createCartItemResponse()

        whenever(cartItemCommandService.addCartItem(1L, request)).thenReturn(response)

        val result = controller.addCartItem(userPrincipal, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(result.body!!.data!!.productId).isEqualTo(100L)
    }

    @Test
    fun 장바구니_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val items = listOf(createCartItemResponse())
        val slice = SliceImpl(items, pageable, false)

        whenever(cartItemQueryService.getCartItems(1L, pageable)).thenReturn(slice)

        val result = controller.getCartItems(userPrincipal, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.content).hasSize(1)
    }

    @Test
    fun 장바구니_수량을_변경한다() {
        val request = CartItemUpdateRequest(quantity = 5)
        val response = createCartItemResponse()

        whenever(cartItemCommandService.updateCartItemQuantity(1L, 1L, request)).thenReturn(response)

        val result = controller.updateCartItemQuantity(userPrincipal, 1L, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.id).isEqualTo(1L)
    }

    @Test
    fun 장바구니에서_상품을_삭제한다() {
        val result = controller.removeCartItem(userPrincipal, 1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }
}
