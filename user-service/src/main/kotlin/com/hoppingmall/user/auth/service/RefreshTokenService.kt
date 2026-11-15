package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.domain.SessionRevokeReason

interface RefreshTokenService {

    fun issue(userId: Long): IssuedRefreshToken

    fun rotate(presentedToken: String): IssuedRefreshToken

    fun revokeSession(userId: Long, sessionId: String?)

    fun revokeAll(userId: Long, reason: SessionRevokeReason)
}
