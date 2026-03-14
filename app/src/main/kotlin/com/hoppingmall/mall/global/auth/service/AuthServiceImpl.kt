package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.mall.global.auth.dto.response.TokenRefreshResponse
import com.hoppingmall.mall.global.jwt.JwtProperties
import com.hoppingmall.mall.global.jwt.TokenProvider
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import com.hoppingmall.mall.user.service.user.UserQueryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthServiceImpl(
    private val userQueryService: UserQueryService,
    private val tokenProvider: TokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val jwtProperties: JwtProperties,
    private val userRepository: UserRepository,
    private val accessTokenBlacklistRepository: AccessTokenBlacklistRepository
) : AuthService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun login(request: SignInRequest): SignInResponse {
        val user = userQueryService.authenticate(request)
        log.info("로그인 성공: userId={}, role={}", user.id, user.getRole())

        val accessToken  = tokenProvider.generateAccessToken(user.id!!, user.getRole())
        val refreshToken = tokenProvider.generateRefreshToken(user.id!!)

        refreshTokenService.rotateRefreshToken(
            userId = user.id!!,
            newToken = refreshToken,
            ttl = jwtProperties.refreshExpirationMs
        )

        return SignInResponse(accessToken, refreshToken)
    }

    override fun refreshAccessToken(refreshToken: String): TokenRefreshResponse {
        val userId = tokenProvider.parseRefreshToken(refreshToken)

        refreshTokenService.validate(userId, refreshToken)

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException() }

        val newAccessToken = tokenProvider.generateAccessToken(user.id!!, user.getRole())
        val newRefreshToken = tokenProvider.generateRefreshToken(user.id!!)

        refreshTokenService.rotateRefreshToken(
            userId = user.id!!,
            newToken = newRefreshToken,
            ttl = jwtProperties.refreshExpirationMs
        )

        return TokenRefreshResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
    }

    override fun logout(accessToken: String) {
        val userId = tokenProvider.parseAccessToken(accessToken)
        val remainingMs = tokenProvider.getRemainingExpirationMs(accessToken)
        accessTokenBlacklistRepository.add(accessToken, remainingMs)
        refreshTokenService.delete(userId)
        log.info("로그아웃: userId={}", userId)
    }
}
