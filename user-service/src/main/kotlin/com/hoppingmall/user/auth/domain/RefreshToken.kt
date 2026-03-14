package com.hoppingmall.user.auth.domain

data class RefreshToken(
    val userId: Long,
    val token: String,
    val ttl: Long
)
