package com.hoppingmall.user.auth.service

data class IssuedRefreshToken(
    val userId: Long,
    val sessionId: String,
    val token: String
) {
    override fun toString(): String = "IssuedRefreshToken(userId=$userId, sessionId=$sessionId)"
}
