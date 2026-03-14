package com.hoppingmall.user.auth.dto

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String
)
