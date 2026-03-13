package com.hoppingmall.mall.wishlist.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.mall.wishlist.dto.response.WishlistResponse
import com.hoppingmall.mall.wishlist.service.WishlistCommandService
import com.hoppingmall.mall.wishlist.service.WishlistQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@DisplayName("WishlistController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class WishlistControllerTest {

    private val wishlistCommandService: WishlistCommandService = mock()
    private val wishlistQueryService: WishlistQueryService = mock()
    private val controller = WishlistController(wishlistCommandService, wishlistQueryService)

    @Nested
    @DisplayName("addWishlist")
    inner class AddWishlist {
        @Test
        fun 찜_추가_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val request = WishlistCreateRequest(productId = 100L)
            val expectedResponse = WishlistResponse(
                id = 1L,
                productId = 100L,
                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
            )

            // Context
            whenever(wishlistCommandService.addWishlist(userPrincipal.getUserId(), request)).thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<WishlistResponse>> = controller.addWishlist(userPrincipal, request)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponse, response.body?.data)
            verify(wishlistCommandService).addWishlist(userPrincipal.getUserId(), request)
        }
    }

    @Nested
    @DisplayName("removeWishlist")
    inner class RemoveWishlist {
        @Test
        fun 찜_삭제_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val wishlistId = 1L

            // Context
            doNothing().whenever(wishlistCommandService).removeWishlist(userPrincipal.getUserId(), wishlistId)

            // Interaction
            val response: ResponseEntity<ApiResponse<Unit>> = controller.removeWishlist(userPrincipal, wishlistId)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(Unit, response.body?.data)
            verify(wishlistCommandService).removeWishlist(userPrincipal.getUserId(), wishlistId)
        }
    }

    @Nested
    @DisplayName("getWishlists")
    inner class GetWishlists {
        @Test
        fun 찜_목록_조회_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val pageable = PageRequest.of(0, 20)
            val expectedResponses = listOf(
                WishlistResponse(
                    id = 1L,
                    productId = 100L,
                    createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
                ),
                WishlistResponse(
                    id = 2L,
                    productId = 200L,
                    createdAt = LocalDateTime.of(2026, 1, 2, 0, 0)
                )
            )
            val expectedPage: Page<WishlistResponse> = PageImpl(expectedResponses, pageable, 2)

            // Context
            whenever(wishlistQueryService.getWishlists(userPrincipal.getUserId(), pageable)).thenReturn(expectedPage)

            // Interaction
            val response: ResponseEntity<ApiResponse<Page<WishlistResponse>>> = controller.getWishlists(userPrincipal, pageable)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedPage, response.body?.data)
            verify(wishlistQueryService).getWishlists(userPrincipal.getUserId(), pageable)
        }
    }

    @Nested
    @DisplayName("isWishlisted")
    inner class IsWishlisted {
        @Test
        fun 찜_여부_확인_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "ROLE_USER")
            val productId = 100L

            // Context
            whenever(wishlistQueryService.isWishlisted(userPrincipal.getUserId(), productId)).thenReturn(true)

            // Interaction
            val response: ResponseEntity<ApiResponse<Boolean>> = controller.isWishlisted(userPrincipal, productId)

            // Assertions
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(true, response.body?.data)
            verify(wishlistQueryService).isWishlisted(userPrincipal.getUserId(), productId)
        }
    }
}
