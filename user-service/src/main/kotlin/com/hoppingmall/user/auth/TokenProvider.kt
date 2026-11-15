package com.hoppingmall.user.auth

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.common.enums.Role
import java.time.Instant

interface TokenProvider {

    fun generateAccessToken(userId: Long, role: Role, sessionId: String): String
    fun generateRefreshToken(userId: Long, sessionId: String, generation: Long, issuedAt: Instant, expiresAt: Instant): String

    fun parseAccessToken(token: String): Long
    fun parseRefreshClaims(token: String): RefreshTokenClaims

    fun getSessionId(token: String): String?

    fun validateToken(token: String): Boolean

    fun getUserIdFromToken(token: String): Long
    fun getUserRoleFromToken(token: String): Role
    fun getUserPrincipal(token: String): UserPrincipal

    fun getRemainingExpirationMs(token: String): Long
}
