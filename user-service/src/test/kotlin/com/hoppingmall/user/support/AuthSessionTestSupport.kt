package com.hoppingmall.user.support

import com.hoppingmall.user.auth.domain.SessionRevokeReason
import com.hoppingmall.user.auth.domain.UserSession
import com.hoppingmall.user.auth.domain.repository.UserSessionRepository
import com.hoppingmall.user.auth.service.IssuedRefreshToken
import com.hoppingmall.user.auth.service.RefreshTokenService
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

fun issuingRefreshTokenService(): RefreshTokenService = mock {
    on { issue(any()) } doAnswer { IssuedRefreshToken(it.getArgument(0), "session-${it.getArgument<Long>(0)}", "refresh-token") }
}

class InMemoryUserSessions {
    val sessions = mutableListOf<UserSession>()
    private val ids = AtomicLong()

    val repository: UserSessionRepository = mock {
        on { save(any<UserSession>()) } doAnswer { invocation ->
            invocation.getArgument<UserSession>(0).also { session ->
                if (session.id == null) {
                    session.withId(ids.incrementAndGet())
                    sessions += session
                }
            }
        }
        on { findForUpdateBySessionId(any()) } doAnswer { invocation ->
            sessions.find { it.sessionId == invocation.getArgument<String>(0) }
        }
        on { findActiveByUserId(any(), any()) } doAnswer { invocation ->
            val userId = invocation.getArgument<Long>(0)
            val now = invocation.getArgument<LocalDateTime>(1)
            sessions.filter { it.userId == userId && it.isActive(now) }
                .sortedWith(compareBy<UserSession>({ it.tokenIssuedAt }, { it.id }))
        }
        on { deleteEndedByUserId(any(), any()) } doAnswer { invocation ->
            val userId = invocation.getArgument<Long>(0)
            val now = invocation.getArgument<LocalDateTime>(1)
            val before = sessions.size
            sessions.removeIf { it.userId == userId && !it.isActive(now) }
            before - sessions.size
        }
        on { revokeAllActiveByUserId(any(), any(), any()) } doAnswer { invocation ->
            val userId = invocation.getArgument<Long>(0)
            sessions.filter { it.userId == userId && it.revokedAt == null }
                .onEach { it.revoke(invocation.getArgument(1), invocation.getArgument<SessionRevokeReason>(2)) }
                .size
        }
    }
}
