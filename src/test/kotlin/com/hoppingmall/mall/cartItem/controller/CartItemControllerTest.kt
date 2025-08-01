package com.hoppingmall.mall.cartItem.controller

import com.hoppingmall.mall.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.mall.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import com.hoppingmall.mall.cartItem.service.CartItemCommandService
import com.hoppingmall.mall.cartItem.service.CartItemQueryService
import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity

@DisplayName("CartItemController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CartItemControllerTest {

    private val cartItemCommandService: CartItemCommandService = mock()
    private val cartItemQueryService: CartItemQueryService = mock()
    private val controller = CartItemController(cartItemCommandService, cartItemQueryService)

    @Nested
    @DisplayName("addCartItem")
    inner class AddCartItem {
        @Test
        fun 장바구니_아이템_추가_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val request = CartItemCreateRequest(productId = 100L, quantity = 2)
            val expectedResponse = CartItemResponse(
                id = 1L,
                productId = 100L,
                productName = "테스트 상품",
                productPrice = 15000L,
                productImageUrl = "https://example.com/image.jpg",
                quantity = 2,
                totalPrice = 30000L
            )

            // Context
            whenever(cartItemCommandService.addCartItem(userPrincipal.getUserId(), request)).thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<CartItemResponse>> = controller.addCartItem(userPrincipal, request)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponse, response.body?.data)
            verify(cartItemCommandService).addCartItem(userPrincipal.getUserId(), request)
        }
    }

    @Nested
    @DisplayName("getCartItems")
    inner class GetCartItems {
        @Test
        fun 장바구니_아이템_목록_조회_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val expectedResponses = listOf(
                CartItemResponse(
                    id = 1L,
                    productId = 100L,
                    productName = "테스트 상품 1",
                    productPrice = 15000L,
                    productImageUrl = "https://example.com/image1.jpg",
                    quantity = 2,
                    totalPrice = 30000L
                ),
                CartItemResponse(
                    id = 2L,
                    productId = 200L,
                    productName = "테스트 상품 2",
                    productPrice = 20000L,
                    productImageUrl = "https://example.com/image2.jpg",
                    quantity = 1,
                    totalPrice = 20000L
                )
            )

            // Context
            whenever(cartItemQueryService.getCartItems(userPrincipal.getUserId())).thenReturn(expectedResponses)

            // Interaction
            val response: ResponseEntity<ApiResponse<List<CartItemResponse>>> = controller.getCartItems(userPrincipal)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponses, response.body?.data)
            verify(cartItemQueryService).getCartItems(userPrincipal.getUserId())
        }
    }

    @Nested
    @DisplayName("updateCartItemQuantity")
    inner class UpdateCartItemQuantity {
        @Test
        fun 장바구니_아이템_수량_수정_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val cartItemId = 1L
            val request = CartItemUpdateRequest(quantity = 5)
            val expectedResponse = CartItemResponse(
                id = 1L,
                productId = 100L,
                productName = "테스트 상품",
                productPrice = 15000L,
                productImageUrl = "https://example.com/image.jpg",
                quantity = 5,
                totalPrice = 75000L
            )

            // Context
            whenever(cartItemCommandService.updateCartItemQuantity(userPrincipal.getUserId(), cartItemId, request)).thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<CartItemResponse>> = controller.updateCartItemQuantity(userPrincipal, cartItemId, request)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponse, response.body?.data)
            verify(cartItemCommandService).updateCartItemQuantity(userPrincipal.getUserId(), cartItemId, request)
        }
    }

    @Nested
    @DisplayName("removeCartItem")
    inner class RemoveCartItem {
        @Test
        fun 장바구니_아이템_삭제_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val cartItemId = 1L

            // Context
            doNothing().whenever(cartItemCommandService).removeCartItem(userPrincipal.getUserId(), cartItemId)

            // Interaction
            val response: ResponseEntity<ApiResponse<Unit>> = controller.removeCartItem(userPrincipal, cartItemId)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(Unit, response.body?.data)
            verify(cartItemCommandService).removeCartItem(userPrincipal.getUserId(), cartItemId)
        }
    }
} 