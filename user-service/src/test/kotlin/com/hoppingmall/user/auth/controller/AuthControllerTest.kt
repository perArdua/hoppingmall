package com.hoppingmall.user.auth.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.user.auth.dto.LogoutRequest
import com.hoppingmall.user.auth.dto.TokenRefreshRequest
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.auth.service.AuthService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("AuthController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AuthControllerTest {

    @Mock
    private lateinit var authService: AuthService

    @InjectMocks
    private lateinit var authController: AuthController

    @Test
    fun 토큰_재발급_응답을_ApiResponse로_감싼다() {
        val request = TokenRefreshRequest("refresh-token")
        val expected = TokenRefreshResponse("new-access-token", "new-refresh-token")
        whenever(authService.refreshAccessToken("refresh-token")).thenReturn(expected)

        val response = authController.refreshAccessToken(request)

        assertThat(response).isEqualTo(ApiResponse.success(expected))
        verify(authService).refreshAccessToken("refresh-token")
    }

    @Test
    fun 로그아웃_시_Bearer_접두사_제거_후_서비스에_위임() {
        val response = authController.logout("Bearer access-token", null)

        assertThat(response).isEqualTo(ApiResponse.success(Unit))
        verify(authService).logout("access-token", null)
    }

    @Test
    fun 로그아웃_본문의_리프레시_토큰을_Authorization_없이도_서비스에_넘긴다() {
        val response = authController.logout(null, LogoutRequest("refresh-token"))

        assertThat(response).isEqualTo(ApiResponse.success(Unit))
        verify(authService).logout(null, "refresh-token")
    }

    @Test
    fun 로그아웃_요청을_문자열로_찍어도_리프레시_토큰은_가린다() {
        assertThat(LogoutRequest("refresh-token").toString()).doesNotContain("refresh-token")
    }
}
