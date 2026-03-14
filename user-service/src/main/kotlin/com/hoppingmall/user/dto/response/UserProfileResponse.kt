package com.hoppingmall.user.dto.response

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: String
)
