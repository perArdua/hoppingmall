package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.domain.repository.TokenInvalidationRepository
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
@DisplayName("이전 발급 토큰 무효 시각 기록")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IssuedTokenInvalidatorTest {

    @Mock
    private lateinit var tokenInvalidationRepository: TokenInvalidationRepository

    private val jwtProperties = JwtProperties().apply { accessExpirationMs = 3_600_000 }

    private val clock = Clock.fixed(Instant.ofEpochMilli(1_794_700_000_123L), ZoneOffset.UTC)

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun 트랜잭션_밖이면_지금_시각을_밀리초로_액세스_토큰_수명보다_길게_쓴다() {
        IssuedTokenInvalidator(tokenInvalidationRepository, jwtProperties, clock).invalidateIssuedBefore(7L)

        verify(tokenInvalidationRepository).markInvalidBefore(7L, 1_794_700_000_123L, 3_660_000L)
    }

    @Test
    fun 트랜잭션_안이면_커밋된_뒤에_쓴다() {
        TransactionSynchronizationManager.initSynchronization()

        IssuedTokenInvalidator(tokenInvalidationRepository, jwtProperties, clock).invalidateIssuedBefore(7L)

        verify(tokenInvalidationRepository, never()).markInvalidBefore(any(), any(), any())
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        verify(tokenInvalidationRepository).markInvalidBefore(7L, 1_794_700_000_123L, 3_660_000L)
    }

    @Test
    fun 트랜잭션이_롤백되면_쓰지_않는다() {
        TransactionSynchronizationManager.initSynchronization()

        IssuedTokenInvalidator(tokenInvalidationRepository, jwtProperties, clock).invalidateIssuedBefore(7L)

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(1) }
        verify(tokenInvalidationRepository, never()).markInvalidBefore(any(), any(), any())
    }

    @Test
    fun Redis에_쓰지_못해도_예외를_올리지_않는다() {
        doThrow(RedisConnectionFailureException("down"))
            .whenever(tokenInvalidationRepository).markInvalidBefore(any(), any(), any())

        assertThatCode {
            IssuedTokenInvalidator(tokenInvalidationRepository, jwtProperties, clock).invalidateIssuedBefore(7L)
        }.doesNotThrowAnyException()
    }
}
