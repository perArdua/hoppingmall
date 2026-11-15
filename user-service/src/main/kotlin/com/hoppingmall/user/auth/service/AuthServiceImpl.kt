package com.hoppingmall.user.auth.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.user.auth.TokenProvider
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.auth.exception.InvalidTokenException
import com.hoppingmall.user.config.ratelimit.LoginAttemptLimiter
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.response.SignInResponse
import com.hoppingmall.user.exception.user.LoginAttemptsExceededException
import com.hoppingmall.user.exception.user.UserLoginFailedException
import com.hoppingmall.user.exception.user.UserNotFoundException
import com.hoppingmall.user.service.UserQueryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuthServiceImpl(
    private val userQueryService: UserQueryService,
    private val tokenProvider: TokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val userRepository: UserRepository,
    private val accessTokenBlacklistRepository: AccessTokenBlacklistRepository,
    private val loginAttemptLimiter: LoginAttemptLimiter
) : AuthService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun login(request: SignInRequest, clientIp: String): SignInResponse {
        val remainingBlock = loginAttemptLimiter.remainingBlock(request.email, clientIp)
        if (!remainingBlock.isZero) {
            throw LoginAttemptsExceededException(remainingBlock)
        }
        val user = try {
            userQueryService.authenticate(request)
        } catch (e: UserLoginFailedException) {
            loginAttemptLimiter.recordFailure(request.email, clientIp)
            throw e
        }
        loginAttemptLimiter.reset(request.email, clientIp)
        val userId = user.id!!

        val issued = refreshTokenService.issue(userId)
        val accessToken = tokenProvider.generateAccessToken(userId, user.getRole(), issued.sessionId)
        log.info("로그인 성공: userId={}, role={}, sessionId={}", userId, user.getRole(), issued.sessionId)

        return SignInResponse(accessToken, issued.token)
    }

    override fun refreshAccessToken(refreshToken: String): TokenRefreshResponse {
        val issued = refreshTokenService.rotate(refreshToken)
        val user = userRepository.findByIdOrNull(issued.userId) ?: throw UserNotFoundException()

        return TokenRefreshResponse(
            accessToken = tokenProvider.generateAccessToken(user.id!!, user.getRole(), issued.sessionId),
            refreshToken = issued.token
        )
    }

    override fun logout(accessToken: String?, refreshToken: String?) {
        val validAccessToken = accessToken?.takeIf { runCatching { tokenProvider.parseAccessToken(it) }.isSuccess }
        val refreshClaims = refreshToken?.let { tokenProvider.parseRefreshClaimsAllowingExpired(it) }

        validAccessToken?.let {
            accessTokenBlacklistRepository.add(it, tokenProvider.getRemainingExpirationMs(it))
        }
        val (userId, sessionId) = when {
            refreshClaims != null -> refreshClaims.userId to refreshClaims.sessionId
            validAccessToken != null -> tokenProvider.parseAccessToken(validAccessToken) to tokenProvider.getSessionId(validAccessToken)
            else -> throw InvalidTokenException()
        }
        refreshTokenService.revokeSession(userId, sessionId)
        log.info("로그아웃: userId={}, sessionId={}", userId, sessionId)
    }
}
