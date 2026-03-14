package com.hoppingmall.user.auth.controller

import com.hoppingmall.user.auth.dto.TokenRefreshRequest
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.user.common.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/refresh")
    fun refreshAccessToken(
        @RequestBody request: TokenRefreshRequest
    ): ApiResponse<TokenRefreshResponse> {
        val response = authService.refreshAccessToken(request.refreshToken)
        return ApiResponse.success(response)
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @RequestHeader("Authorization") bearer: String
    ): ApiResponse<Unit> {
        val accessToken = bearer.removePrefix("Bearer ").trim()
        authService.logout(accessToken)
        return ApiResponse.success(Unit)
    }
}
