package com.hoppingmall.mall.global.auth.controller

import com.hoppingmall.mall.global.auth.dto.request.TokenRefreshRequest
import com.hoppingmall.mall.global.auth.dto.response.TokenRefreshResponse
import com.hoppingmall.mall.global.auth.service.AuthService
import com.hoppingmall.mall.global.common.response.ApiResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class AuthControllerTest {

    private val authService: AuthService = mock()
    private val authController = AuthController(authService)

    @Test
    fun `리프레시 토큰 재발급 요청이 성공하면 새로운 액세스 토큰과 리프레시 토큰을 반환한다`() {
        // given
        val request = TokenRefreshRequest("old-refresh-token")
        val expected = TokenRefreshResponse("new-access-token", "new-refresh-token")

        whenever(authService.refreshAccessToken("old-refresh-token")).thenReturn(expected)

        // when
        val response = authController.refreshAccessToken(request)

        // then
        verify(authService).refreshAccessToken("old-refresh-token")
        assertEquals(ApiResponse.success(expected), response)
    }

    @Test
    fun `Authorization 헤더가 주어지면 accessToken을 추출하여 로그아웃 처리한다`() {
        // given
        val bearerToken = "Bearer access-token-123"

        // when
        val response = authController.logout(bearerToken)

        // then
        verify(authService).logout("access-token-123")
        assertEquals(ApiResponse.success(Unit), response)
    }
}