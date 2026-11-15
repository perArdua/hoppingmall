package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.SessionProperties
import com.hoppingmall.user.auth.TokenProvider
import com.hoppingmall.user.auth.domain.SessionRevokeReason
import com.hoppingmall.user.auth.domain.UserSession
import com.hoppingmall.user.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.user.auth.domain.repository.UserSessionRepository
import com.hoppingmall.user.auth.exception.RefreshTokenMismatchException
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import com.hoppingmall.user.auth.exception.RefreshTokenReusedException
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.exception.user.UserNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

@Service
class RefreshTokenServiceImpl(
    private val userSessionRepository: UserSessionRepository,
    private val userRepository: UserRepository,
    private val legacyRefreshTokenRepository: RefreshTokenRepository,
    private val tokenProvider: TokenProvider,
    private val jwtProperties: JwtProperties,
    private val sessionProperties: SessionProperties,
    private val clock: Clock = Clock.systemDefaultZone()
) : RefreshTokenService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun issue(userId: Long): IssuedRefreshToken {
        userRepository.findForUpdateById(userId) ?: throw UserNotFoundException()
        val now = now()
        userSessionRepository.deleteEndedByUserId(userId, now)
        val active = userSessionRepository.findActiveByUserId(userId, now)
        active.take((active.size - sessionProperties.maxSessionsPerUser + 1).coerceAtLeast(0))
            .forEach { it.revoke(now, SessionRevokeReason.EVICTED) }

        val sessionId = UUID.randomUUID().toString()
        val expiresAt = now.plus(jwtProperties.refreshExpirationMs, ChronoUnit.MILLIS)
        val token = mint(userId, sessionId, UserSession.FIRST_GENERATION, now, expiresAt)
        userSessionRepository.save(UserSession.start(sessionId, userId, hash(token), now, expiresAt))
        return IssuedRefreshToken(userId, sessionId, token)
    }

    @Transactional(noRollbackFor = [RefreshTokenReusedException::class])
    override fun rotate(presentedToken: String): IssuedRefreshToken {
        val claims = tokenProvider.parseRefreshClaims(presentedToken)
        val sessionId = claims.sessionId ?: return migrateLegacy(claims.userId, presentedToken)
        val now = now()
        val session = userSessionRepository.findForUpdateBySessionId(sessionId)
            ?.takeIf { it.userId == claims.userId && it.isActive(now) }
            ?: throw RefreshTokenNotFoundException()

        val presentedHash = hash(presentedToken)
        if (session.isCurrent(presentedHash)) {
            val expiresAt = now.plus(jwtProperties.refreshExpirationMs, ChronoUnit.MILLIS)
            val token = mint(session.userId, sessionId, session.generation + 1, now, expiresAt)
            session.rotate(hash(token), now, expiresAt)
            return IssuedRefreshToken(session.userId, sessionId, token)
        }
        if (session.isJustReplaced(presentedHash, now, sessionProperties.reuseGrace)) {
            val token = mint(session.userId, sessionId, session.generation, session.tokenIssuedAt, session.tokenExpiresAt)
            if (!session.isCurrent(hash(token))) throw RefreshTokenMismatchException()
            return IssuedRefreshToken(session.userId, sessionId, token)
        }
        session.revoke(now, SessionRevokeReason.REUSE_DETECTED)
        log.warn("이미 바뀐 리프레시 토큰이 다시 와 세션을 끊음: userId={}, generation={}", session.userId, claims.generation)
        throw RefreshTokenReusedException()
    }

    @Transactional
    override fun revokeSession(userId: Long, sessionId: String?) {
        if (sessionId == null) {
            legacyRefreshTokenRepository.deleteByUserId(userId)
            return
        }
        userSessionRepository.findForUpdateBySessionId(sessionId)
            ?.takeIf { it.userId == userId }
            ?.revoke(now(), SessionRevokeReason.LOGOUT)
    }

    @Transactional
    override fun revokeAll(userId: Long, reason: SessionRevokeReason) {
        userRepository.findForUpdateById(userId) ?: throw UserNotFoundException()
        userSessionRepository.revokeAllActiveByUserId(userId, now(), reason)
        legacyRefreshTokenRepository.deleteByUserId(userId)
    }

    private fun migrateLegacy(userId: Long, presentedToken: String): IssuedRefreshToken {
        val stored = legacyRefreshTokenRepository.findByUserId(userId) ?: throw RefreshTokenNotFoundException()
        if (stored != presentedToken) throw RefreshTokenMismatchException()
        legacyRefreshTokenRepository.deleteByUserId(userId)
        return issue(userId)
    }

    private fun mint(
        userId: Long,
        sessionId: String,
        generation: Long,
        issuedAt: LocalDateTime,
        expiresAt: LocalDateTime
    ): String = tokenProvider.generateRefreshToken(
        userId,
        sessionId,
        generation,
        issuedAt.atZone(clock.zone).toInstant(),
        expiresAt.atZone(clock.zone).toInstant()
    )

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)

    private fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))
}
