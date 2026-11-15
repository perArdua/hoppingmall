package com.hoppingmall.user.auth.controller

import com.hoppingmall.user.auth.cookie.RefreshTokenCookies
import com.hoppingmall.user.auth.dto.LogoutRequest
import com.hoppingmall.user.auth.dto.TokenRefreshRequest
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.CommonErrorCode
import com.hoppingmall.common.UserPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/refresh")
    fun refreshAccessToken(
        @RequestBody(required = false) request: TokenRefreshRequest?,
        @RequestHeader(RefreshTokenCookies.TRANSPORT_HEADER, required = false) transport: String? = null,
        @CookieValue(RefreshTokenCookies.COOKIE_NAME, required = false) cookieToken: String? = null
    ): ApiResponse<TokenRefreshResponse> {
        val refreshToken = request?.refreshToken
            ?: if (RefreshTokenCookies.isCookieTransport(transport)) {
                cookieToken?.takeIf { it.isNotBlank() } ?: throw RefreshTokenNotFoundException()
            } else {
                throw BusinessException(CommonErrorCode.INVALID_INPUT)
            }
        val response = authService.refreshAccessToken(refreshToken)
        return ApiResponse.success(response)
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) bearer: String?,
        @RequestBody(required = false) request: LogoutRequest?,
        @RequestHeader(RefreshTokenCookies.TRANSPORT_HEADER, required = false) transport: String? = null,
        @CookieValue(RefreshTokenCookies.COOKIE_NAME, required = false) cookieToken: String? = null
    ): ApiResponse<Unit> {
        val accessToken = bearer?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }
        val refreshToken = request?.refreshToken
            ?: cookieToken?.takeIf { RefreshTokenCookies.isCookieTransport(transport) && it.isNotBlank() }
        authService.logout(accessToken, refreshToken)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) bearer: String?
    ): ApiResponse<Unit> {
        authService.logoutAll(principal.getUserId(), bearer?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() })
        return ApiResponse.success(Unit)
    }
}
