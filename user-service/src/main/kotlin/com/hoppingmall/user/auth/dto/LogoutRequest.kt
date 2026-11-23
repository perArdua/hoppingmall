package com.hoppingmall.user.auth.dto

data class LogoutRequest(
    val refreshToken: String? = null
) {
    override fun toString(): String = "LogoutRequest(refreshToken=${if (refreshToken == null) "null" else "****"})"
}
