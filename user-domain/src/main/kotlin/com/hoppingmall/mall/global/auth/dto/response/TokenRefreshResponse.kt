package com.hoppingmall.mall.global.auth.dto.response

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String
)
