package com.hoppingmall.user.auth.controller

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.CommonErrorCode
import com.hoppingmall.user.auth.dto.LogoutRequest
import com.hoppingmall.user.auth.dto.TokenRefreshRequest
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import com.hoppingmall.user.auth.service.AuthService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("AuthController 쿠키 방식")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AuthControllerCookieTest {

    @Mock
    private lateinit var authService: AuthService

    @InjectMocks
    private lateinit var authController: AuthController

    @Test
    fun 쿠키_방식이면_본문_없이_쿠키의_토큰으로_재발급한다() {
        whenever(authService.refreshAccessToken("cookie-token")).thenReturn(TokenRefreshResponse("a", "r"))

        authController.refreshAccessToken(null, "cookie", "cookie-token")

        verify(authService).refreshAccessToken("cookie-token")
    }

    @Test
    fun 본문_토큰이_있으면_쿠키보다_본문을_쓴다() {
        whenever(authService.refreshAccessToken("body-token")).thenReturn(TokenRefreshResponse("a", "r"))

        authController.refreshAccessToken(TokenRefreshRequest("body-token"), "cookie", "cookie-token")

        verify(authService).refreshAccessToken("body-token")
    }

    @Test
    fun 쿠키_방식인데_쿠키가_없거나_비었으면_401_A001이다() {
        assertThatThrownBy { authController.refreshAccessToken(null, "cookie", null) }
            .isInstanceOf(RefreshTokenNotFoundException::class.java)
        assertThatThrownBy { authController.refreshAccessToken(null, "cookie", " ") }
            .isInstanceOf(RefreshTokenNotFoundException::class.java)
        verify(authService, never()).refreshAccessToken(any())
    }

    @Test
    fun 헤더_없이_본문도_없으면_쿠키가_있어도_읽지_않고_400이다() {
        assertThatThrownBy { authController.refreshAccessToken(null, null, "cookie-token") }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT)
            }
        verify(authService, never()).refreshAccessToken(any())
    }

    @Test
    fun 쿠키_방식_로그아웃은_본문이_없으면_쿠키의_토큰을_넘긴다() {
        authController.logout(null, null, "cookie", "cookie-token")

        verify(authService).logout(null, "cookie-token")
    }

    @Test
    fun 헤더_없는_로그아웃은_쿠키를_읽지_않는다() {
        authController.logout("Bearer access", null, null, "cookie-token")

        verify(authService).logout("access", null)
    }

    @Test
    fun 로그아웃_본문_토큰은_쿠키보다_먼저다() {
        authController.logout(null, LogoutRequest("body-token"), "cookie", "cookie-token")

        verify(authService).logout(null, "body-token")
    }
}
