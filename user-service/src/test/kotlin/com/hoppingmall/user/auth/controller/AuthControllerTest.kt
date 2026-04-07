package com.hoppingmall.user.auth.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.user.auth.dto.TokenRefreshRequest
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.auth.service.AuthService
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
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("AuthController 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AuthControllerTest {

    @Mock
    private lateinit var authService: AuthService

    @InjectMocks
    private lateinit var authController: AuthController

    @Test
    fun refreshAccessToken은_응답을_ApiResponse로_감싼다() {
        val request = TokenRefreshRequest("refresh-token")
        val expected = TokenRefreshResponse("new-access-token", "new-refresh-token")
        whenever(authService.refreshAccessToken("refresh-token")).thenReturn(expected)

        val response = authController.refreshAccessToken(request)

        assertEquals(ApiResponse.success(expected), response)
        verify(authService).refreshAccessToken("refresh-token")
    }

    @Test
    fun logout은_Bearer_접두사를_제거하고_service에_위임한다() {
        val response = authController.logout("Bearer access-token")

        assertEquals(ApiResponse.success(Unit), response)
        verify(authService).logout("access-token")
    }
}
