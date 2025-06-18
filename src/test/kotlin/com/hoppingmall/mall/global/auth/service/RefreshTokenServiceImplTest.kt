package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.domain.RefreshToken
import com.hoppingmall.mall.global.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.mall.global.auth.exception.RefreshTokenMismatchException
import com.hoppingmall.mall.global.auth.exception.RefreshTokenNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class RefreshTokenServiceImplTest {

    private val refreshTokenRepository: RefreshTokenRepository = mock()
    private lateinit var refreshTokenService: RefreshTokenServiceImpl

    @BeforeEach
    fun setUp() {
        refreshTokenService = RefreshTokenServiceImpl(refreshTokenRepository)
    }

    @Test
    fun `기존 리프레시 토큰이 존재할 때 새 토큰으로 교체되며 저장된다`() {
        // given
        val userId = 1L
        val token = "new-token"
        val ttl = 3600L
        val expected = RefreshToken(userId, token, ttl)
        val captor = argumentCaptor<RefreshToken>()

        // when
        val result = refreshTokenService.rotateRefreshToken(userId, token, ttl)

        // then
        verify(refreshTokenRepository).deleteByUserId(userId)
        verify(refreshTokenRepository).save(captor.capture())
        assert(result == expected)
        assert(captor.firstValue == expected)
    }

    @Test
    fun `저장된 리프레시 토큰과 일치하는 경우 검증에 성공한다`() {
        // given
        val userId = 2L
        whenever(refreshTokenRepository.findByUserId(userId)).thenReturn("token-abc")

        // when & then
        refreshTokenService.validate(userId, "token-abc")
    }

    @Test
    fun `리프레시 토큰이 저장되어 있지 않으면 예외가 발생한다`() {
        // given
        val userId = 3L
        whenever(refreshTokenRepository.findByUserId(userId)).thenReturn(null)

        // when & then
        assertThrows<RefreshTokenNotFoundException> {
            refreshTokenService.validate(userId, "any-token")
        }
    }

    @Test
    fun `저장된 리프레시 토큰과 요청 토큰이 다르면 예외가 발생한다`() {
        // given
        val userId = 4L
        whenever(refreshTokenRepository.findByUserId(userId)).thenReturn("stored-token")

        // when & then
        assertThrows<RefreshTokenMismatchException> {
            refreshTokenService.validate(userId, "different-token")
        }
    }

    @Test
    fun `리프레시 토큰 삭제 시 userId 기준으로 삭제된다`() {
        // given
        val userId = 5L

        // when
        refreshTokenService.delete(userId)

        // then
        verify(refreshTokenRepository).deleteByUserId(userId)
    }
}
