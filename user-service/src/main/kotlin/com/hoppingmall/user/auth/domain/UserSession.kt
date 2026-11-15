package com.hoppingmall.user.auth.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_sessions",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_sessions_session_id", columnNames = ["session_id"])],
    indexes = [Index(name = "idx_user_sessions_user_id_token_issued_at", columnList = "user_id, token_issued_at")]
)
class UserSession private constructor(
    @Column(name = "session_id", nullable = false, updatable = false, length = 36)
    val sessionId: String,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    tokenHash: String,
    tokenIssuedAt: LocalDateTime,
    tokenExpiresAt: LocalDateTime
) : BaseEntity() {

    @Column(name = "token_hash", nullable = false, length = 64)
    var tokenHash: String = tokenHash
        private set

    @Column(name = "previous_token_hash", length = 64)
    var previousTokenHash: String? = null
        private set

    @Column(name = "generation", nullable = false)
    var generation: Long = FIRST_GENERATION
        private set

    @Column(name = "token_issued_at", nullable = false)
    var tokenIssuedAt: LocalDateTime = tokenIssuedAt
        private set

    @Column(name = "token_expires_at", nullable = false)
    var tokenExpiresAt: LocalDateTime = tokenExpiresAt
        private set

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null
        private set

    @Convert(converter = SessionRevokeReasonConverter::class)
    @Column(name = "revoke_reason", length = 32)
    var revokeReason: SessionRevokeReason? = null
        private set

    fun isActive(now: LocalDateTime): Boolean = revokedAt == null && tokenExpiresAt.isAfter(now)

    fun isCurrent(presentedHash: String): Boolean = tokenHash == presentedHash

    fun isJustReplaced(presentedHash: String, now: LocalDateTime, grace: Duration): Boolean =
        previousTokenHash == presentedHash && !tokenIssuedAt.plus(grace).isBefore(now)

    fun rotate(newTokenHash: String, issuedAt: LocalDateTime, expiresAt: LocalDateTime) {
        previousTokenHash = tokenHash
        tokenHash = newTokenHash
        generation += 1
        tokenIssuedAt = issuedAt
        tokenExpiresAt = expiresAt
    }

    fun revoke(now: LocalDateTime, reason: SessionRevokeReason) {
        if (revokedAt != null) return
        revokedAt = now
        revokeReason = reason
    }

    companion object {
        const val FIRST_GENERATION = 1L

        fun start(
            sessionId: String,
            userId: Long,
            tokenHash: String,
            issuedAt: LocalDateTime,
            expiresAt: LocalDateTime
        ): UserSession = UserSession(sessionId, userId, tokenHash, issuedAt, expiresAt)
    }
}
