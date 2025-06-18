package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.domain.RefreshToken

interface RefreshTokenService{

    fun rotateRefreshToken(userId: Long, newToken: String, ttl: Long): RefreshToken

    fun validate(userId: Long, presentedToken: String)

    fun delete(userId: Long)
}
