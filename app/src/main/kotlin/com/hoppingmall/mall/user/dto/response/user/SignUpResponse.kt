package com.hoppingmall.mall.user.dto.response.user

import com.hoppingmall.mall.global.enums.Role

data class SignUpResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role
)
