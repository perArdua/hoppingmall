package com.hoppingmall.user.auth

import com.hoppingmall.user.auth.exception.InvalidTokenException
import com.hoppingmall.user.common.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("TokenProviderImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TokenProviderImplTest {

    private lateinit var tokenProvider: TokenProviderImpl

    @BeforeEach
    fun setUp() {
        val jwtProperties = JwtProperties().apply {
            secret = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256"
            accessExpirationMs = 3600000
            refreshExpirationMs = 86400000
        }
        tokenProvider = TokenProviderImpl(jwtProperties)
    }

    @Test
    fun 액세스_토큰을_생성한다() {
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER)

        assertThat(token).isNotBlank()
    }

    @Test
    fun 리프레시_토큰을_생성한다() {
        val token = tokenProvider.generateRefreshToken(1L)

        assertThat(token).isNotBlank()
    }

    @Test
    fun 액세스_토큰에서_사용자_ID를_파싱한다() {
        val token = tokenProvider.generateAccessToken(42L, Role.SELLER)

        val userId = tokenProvider.parseAccessToken(token)

        assertThat(userId).isEqualTo(42L)
    }

    @Test
    fun 리프레시_토큰에서_사용자_ID를_파싱한다() {
        val token = tokenProvider.generateRefreshToken(42L)

        val userId = tokenProvider.parseRefreshToken(token)

        assertThat(userId).isEqualTo(42L)
    }

    @Test
    fun 유효한_토큰을_검증한다() {
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER)

        val result = tokenProvider.validateToken(token)

        assertThat(result).isTrue()
    }

    @Test
    fun 잘못된_토큰을_검증하면_예외가_발생한다() {
        assertThatThrownBy { tokenProvider.validateToken("invalid-token") }
            .isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun 토큰에서_사용자_ID를_가져온다() {
        val token = tokenProvider.generateAccessToken(10L, Role.ADMIN)

        val userId = tokenProvider.getUserIdFromToken(token)

        assertThat(userId).isEqualTo(10L)
    }

    @Test
    fun 토큰에서_사용자_역할을_가져온다() {
        val token = tokenProvider.generateAccessToken(1L, Role.SELLER)

        val role = tokenProvider.getUserRoleFromToken(token)

        assertThat(role).isEqualTo(Role.SELLER)
    }

    @Test
    fun 토큰에서_UserPrincipal을_가져온다() {
        val token = tokenProvider.generateAccessToken(5L, Role.BUYER)

        val principal = tokenProvider.getUserPrincipal(token)

        assertThat(principal.getUserId()).isEqualTo(5L)
    }

    @Test
    fun 토큰의_남은_만료_시간을_가져온다() {
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER)

        val remaining = tokenProvider.getRemainingExpirationMs(token)

        assertThat(remaining).isPositive()
    }
}
