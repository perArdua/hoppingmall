package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.response.SignInResponse

interface AuthService {
    fun login(request: SignInRequest): SignInResponse

    fun refreshAccessToken(refreshToken: String): TokenRefreshResponse

    fun logout(accessToken: String)
}
