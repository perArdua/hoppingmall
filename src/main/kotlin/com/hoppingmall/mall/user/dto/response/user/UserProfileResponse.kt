package com.hoppingmall.mall.user.dto.response.user

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: String
) 