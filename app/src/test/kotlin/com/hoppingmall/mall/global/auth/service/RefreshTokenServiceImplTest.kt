package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.domain.RefreshToken
import com.hoppingmall.mall.global.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.mall.global.auth.exception.RefreshTokenMismatchException
import com.hoppingmall.mall.global.auth.exception.RefreshTokenNotFoundException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*

@DisplayName("RefreshTokenServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenServiceImplTest {

    private val refreshTokenRepository: RefreshTokenRepository = mock()
    private lateinit var refreshTokenService: RefreshTokenServiceImpl

    @BeforeEach
    fun setUp() {
        refreshTokenService = RefreshTokenServiceImpl(refreshTokenRepository)
    }

    @Nested
    @DisplayName("rotateRefreshToken")
    inner class RotateRefreshToken {
        @Test
        fun 기존_refreshToken이_존재할_때_새_토큰으로_교체되어_저장된다() {
            val userId = 1L
            val token = "new-token"
            val ttl = 3600L
            val expected = RefreshToken(userId, token, ttl)
            val captor = argumentCaptor<RefreshToken>()

            val result = refreshTokenService.rotateRefreshToken(userId, token, ttl)

            verify(refreshTokenRepository).deleteByUserId(userId)
            verify(refreshTokenRepository).save(captor.capture())
            assert(result == expected)
            assert(captor.firstValue == expected)
        }
    }

    @Nested
    @DisplayName("validate")
    inner class Validate {
        @Test
        fun 저장된_refreshToken과_일치하면_검증에_성공한다() {
            val userId = 2L
            whenever(refreshTokenRepository.findByUserId(userId)).thenReturn("token-abc")

            refreshTokenService.validate(userId, "token-abc")
        }

        @Test
        fun refreshToken이_저장되어_있지_않으면_예외가_발생한다() {
            val userId = 3L
            whenever(refreshTokenRepository.findByUserId(userId)).thenReturn(null)

            assertThrows<RefreshTokenNotFoundException> {
                refreshTokenService.validate(userId, "any-token")
            }
        }

        @Test
        fun 저장된_refreshToken과_요청_토큰이_다르면_예외가_발생한다() {
            val userId = 4L
            whenever(refreshTokenRepository.findByUserId(userId)).thenReturn("stored-token")

            assertThrows<RefreshTokenMismatchException> {
                refreshTokenService.validate(userId, "different-token")
            }
        }
    }

    @Nested
    @DisplayName("delete")
    inner class Delete {
        @Test
        fun refreshToken_삭제_시_userId_기준으로_삭제된다() {
            val userId = 5L

            refreshTokenService.delete(userId)

            verify(refreshTokenRepository).deleteByUserId(userId)
        }
    }
}
