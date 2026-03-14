package com.hoppingmall.user.dto.response

import com.hoppingmall.user.common.enums.Role

data class SignUpResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role
)
