package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.domain.RefreshToken
import com.hoppingmall.mall.global.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.mall.global.auth.exception.RefreshTokenMismatchException
import com.hoppingmall.mall.global.auth.exception.RefreshTokenNotFoundException
import org.springframework.stereotype.Service

@Service
class RefreshTokenServiceImpl(
    private val refreshTokenRepository: RefreshTokenRepository
) : RefreshTokenService {

    override fun rotateRefreshToken(userId: Long, newToken: String, ttl: Long): RefreshToken {
        refreshTokenRepository.deleteByUserId(userId)

        val refreshToken = RefreshToken(userId = userId, token = newToken, ttl = ttl)
        refreshTokenRepository.save(refreshToken)

        return refreshToken
    }

    override fun validate(userId: Long, presentedToken: String) {
        val stored = refreshTokenRepository.findByUserId(userId)
            ?: throw RefreshTokenNotFoundException()

        if (stored != presentedToken) {
            throw RefreshTokenMismatchException()
        }
    }

    override fun delete(userId: Long) {
        refreshTokenRepository.deleteByUserId(userId)
    }
}