package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.enums.Role

interface TokenProvider {

    fun generateAccessToken(userId: Long, role: Role): String
    fun generateRefreshToken(userId: Long): String

    fun parseAccessToken(token: String): Long
    fun parseRefreshToken(token: String): Long

    fun validateToken(token: String): Boolean

    fun getUserIdFromToken(token: String): Long
    fun getUserRoleFromToken(token: String): Role
    fun getUserPrincipal(token: String): UserPrincipal
}