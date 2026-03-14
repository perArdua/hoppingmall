package com.hoppingmall.user.dto.response

data class SignInResponse(
    val accessToken: String,
    val refreshToken: String
)
