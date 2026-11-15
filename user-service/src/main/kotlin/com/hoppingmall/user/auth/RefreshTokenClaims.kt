package com.hoppingmall.user.auth

data class RefreshTokenClaims(
    val userId: Long,
    val sessionId: String?,
    val generation: Long?
)
