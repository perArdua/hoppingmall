package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.domain.repository.TokenInvalidationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock

@Component
class IssuedTokenInvalidator(
    private val tokenInvalidationRepository: TokenInvalidationRepository,
    private val jwtProperties: JwtProperties,
    private val clock: Clock = Clock.systemUTC()
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun invalidateIssuedBefore(userId: Long) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write(userId)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = write(userId)
        })
    }

    private fun write(userId: Long) {
        try {
            tokenInvalidationRepository.markInvalidBefore(
                userId,
                clock.millis(),
                jwtProperties.accessExpirationMs + TTL_MARGIN_MS
            )
        } catch (ex: RuntimeException) {
            log.warn(
                "이전 발급 토큰 무효 시각을 Redis에 쓰지 못함, 이미 나간 액세스 토큰은 만료까지 통함: userId={}, error={}",
                userId,
                ex.javaClass.simpleName
            )
        }
    }

    companion object {
        private const val TTL_MARGIN_MS = 60_000L
    }
}
