package com.hoppingmall.mall.global.auth.controller

import com.hoppingmall.mall.global.auth.dto.request.TokenRefreshRequest
import com.hoppingmall.mall.global.auth.dto.response.TokenRefreshResponse
import com.hoppingmall.mall.global.auth.service.AuthService
import com.hoppingmall.mall.global.common.response.ApiResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.*

@DisplayName("AuthController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AuthControllerTest {

    private val authService: AuthService = mock()
    private val authController = AuthController(authService)

    @Nested
    @DisplayName("refreshAccessToken")
    inner class RefreshAccessToken {
        @Test
        fun 리프레시_토큰_재발급_요청이_성공하면_새로운_액세스_토큰과_리프레시_토큰을_반환한다() {
            val request = TokenRefreshRequest("old-refresh-token")
            val expected = TokenRefreshResponse("new-access-token", "new-refresh-token")
            whenever(authService.refreshAccessToken("old-refresh-token")).thenReturn(expected)

            val response = authController.refreshAccessToken(request)

            verify(authService).refreshAccessToken("old-refresh-token")
            assertEquals(ApiResponse.success(expected), response)
        }
    }

    @Nested
    @DisplayName("logout")
    inner class Logout {
        @Test
        fun Authorization_헤더가_주어지면_accessToken을_추출하여_로그아웃_처리한다() {
            val bearerToken = "Bearer access-token-123"

            val response = authController.logout(bearerToken)

            verify(authService).logout("access-token-123")
            assertEquals(ApiResponse.success(Unit), response)
        }
    }
}