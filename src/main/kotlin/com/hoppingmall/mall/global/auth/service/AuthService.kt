package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.dto.response.TokenRefreshResponse
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse

interface AuthService {
    fun login(request: SignInRequest): SignInResponse

    fun refreshAccessToken(refreshToken: String): TokenRefreshResponse

    fun logout(accessToken: String)
}