package com.hoppingmall.user.auth.domain.repository

import com.hoppingmall.user.auth.domain.SessionRevokeReason
import com.hoppingmall.user.auth.domain.UserSession
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UserSessionRepository : JpaRepository<UserSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.sessionId = :sessionId")
    fun findForUpdateBySessionId(@Param("sessionId") sessionId: String): UserSession?

    @Query(
        "select s from UserSession s where s.userId = :userId and s.revokedAt is null and s.tokenExpiresAt > :now " +
            "order by s.tokenIssuedAt asc, s.id asc"
    )
    fun findActiveByUserId(@Param("userId") userId: Long, @Param("now") now: LocalDateTime): List<UserSession>

    @Modifying
    @Query("delete from UserSession s where s.userId = :userId and (s.revokedAt is not null or s.tokenExpiresAt <= :now)")
    fun deleteEndedByUserId(@Param("userId") userId: Long, @Param("now") now: LocalDateTime): Int

    @Modifying
    @Query(
        "update UserSession s set s.revokedAt = :now, s.revokeReason = :reason " +
            "where s.userId = :userId and s.revokedAt is null"
    )
    fun revokeAllActiveByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
        @Param("reason") reason: SessionRevokeReason
    ): Int
}
