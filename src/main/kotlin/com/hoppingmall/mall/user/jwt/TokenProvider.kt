package com.hoppingmall.mall.user.jwt

import com.hoppingmall.mall.global.enums.Role

interface TokenProvider {
    fun generateToken(userId: Long, role: Role): String
}
