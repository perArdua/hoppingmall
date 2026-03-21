package com.hoppingmall.user.auth

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.common.enums.Role

interface TokenProvider {

    fun generateAccessToken(userId: Long, role: Role): String
    fun generateRefreshToken(userId: Long): String

    fun parseAccessToken(token: String): Long
    fun parseRefreshToken(token: String): Long

    fun validateToken(token: String): Boolean

    fun getUserIdFromToken(token: String): Long
    fun getUserRoleFromToken(token: String): Role
    fun getUserPrincipal(token: String): UserPrincipal

    fun getRemainingExpirationMs(token: String): Long
}
