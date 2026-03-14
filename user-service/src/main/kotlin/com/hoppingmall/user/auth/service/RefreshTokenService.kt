package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.domain.RefreshToken

interface RefreshTokenService {

    fun rotateRefreshToken(userId: Long, newToken: String, ttl: Long): RefreshToken

    fun validate(userId: Long, presentedToken: String)

    fun delete(userId: Long)
}
