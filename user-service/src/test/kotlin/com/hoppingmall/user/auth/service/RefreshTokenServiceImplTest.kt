package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.domain.RefreshToken
import com.hoppingmall.user.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.user.auth.exception.RefreshTokenMismatchException
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("RefreshTokenServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenServiceImplTest {

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMocks
    private lateinit var refreshTokenService: RefreshTokenServiceImpl

    @Test
    fun 토큰_회전_시_기존_토큰_삭제_후_새_토큰_저장() {
        val result = refreshTokenService.rotateRefreshToken(userId = 1L, newToken = "new-token", ttl = 3600L)

        assertThat(result.userId).isEqualTo(1L)
        assertThat(result.token).isEqualTo("new-token")
        assertThat(result.ttl).isEqualTo(3600L)
        verify(refreshTokenRepository).deleteByUserId(1L)
        verify(refreshTokenRepository).save(argThat<RefreshToken> {
            userId == 1L && token == "new-token" && ttl == 3600L
        })
    }

    @Test
    fun 저장된_토큰과_일치하면_검증_성공() {
        whenever(refreshTokenRepository.findByUserId(2L)).thenReturn("refresh-token")

        refreshTokenService.validate(2L, "refresh-token")

        verify(refreshTokenRepository).findByUserId(2L)
    }

    @Test
    fun 저장된_토큰이_없으면_예외_발생() {
        whenever(refreshTokenRepository.findByUserId(3L)).thenReturn(null)

        assertThatThrownBy { refreshTokenService.validate(3L, "missing") }
            .isInstanceOf(RefreshTokenNotFoundException::class.java)
    }

    @Test
    fun 저장된_토큰과_다르면_예외_발생() {
        whenever(refreshTokenRepository.findByUserId(4L)).thenReturn("stored-token")

        assertThatThrownBy { refreshTokenService.validate(4L, "different-token") }
            .isInstanceOf(RefreshTokenMismatchException::class.java)
    }

    @Test
    fun 삭제_시_리프레시_토큰_제거() {
        refreshTokenService.delete(5L)

        verify(refreshTokenRepository).deleteByUserId(5L)
    }
}
