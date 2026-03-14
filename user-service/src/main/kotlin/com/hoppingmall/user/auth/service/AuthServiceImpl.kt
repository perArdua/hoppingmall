package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.TokenProvider
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.response.SignInResponse
import com.hoppingmall.user.exception.user.UserNotFoundException
import com.hoppingmall.user.service.UserQueryService
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

        val accessToken = tokenProvider.generateAccessToken(user.id!!, user.getRole())
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
