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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

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
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER, SESSION_ID)

        assertThat(token).isNotBlank()
    }

    @Test
    fun 리프레시_토큰을_생성한다() {
        val token = tokenProvider.generateRefreshToken(1L, SESSION_ID, 1L, ISSUED_AT, EXPIRES_AT)

        assertThat(token).isNotBlank()
    }

    @Test
    fun 같은_세션_세대_시각이면_같은_리프레시_토큰을_만든다() {
        val first = tokenProvider.generateRefreshToken(1L, SESSION_ID, 3L, ISSUED_AT, EXPIRES_AT)
        val second = tokenProvider.generateRefreshToken(1L, SESSION_ID, 3L, ISSUED_AT, EXPIRES_AT)

        assertThat(second).isEqualTo(first)
        assertThat(tokenProvider.generateRefreshToken(1L, SESSION_ID, 4L, ISSUED_AT, EXPIRES_AT)).isNotEqualTo(first)
    }

    @Test
    fun 액세스_토큰에_세션_id를_싣는다() {
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER, SESSION_ID)

        assertThat(tokenProvider.getSessionId(token)).isEqualTo(SESSION_ID)
    }

    @Test
    fun 액세스_토큰에서_사용자_ID를_파싱한다() {
        val token = tokenProvider.generateAccessToken(42L, Role.SELLER, SESSION_ID)

        val userId = tokenProvider.parseAccessToken(token)

        assertThat(userId).isEqualTo(42L)
    }

    @Test
    fun 리프레시_토큰에서_사용자_ID와_세션_세대를_파싱한다() {
        val token = tokenProvider.generateRefreshToken(42L, SESSION_ID, 2L, ISSUED_AT, EXPIRES_AT)

        val claims = tokenProvider.parseRefreshClaims(token)

        assertThat(claims).isEqualTo(RefreshTokenClaims(userId = 42L, sessionId = SESSION_ID, generation = 2L))
    }

    @Test
    fun 유효한_토큰을_검증한다() {
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER, SESSION_ID)

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
        val token = tokenProvider.generateAccessToken(10L, Role.ADMIN, SESSION_ID)

        val userId = tokenProvider.getUserIdFromToken(token)

        assertThat(userId).isEqualTo(10L)
    }

    @Test
    fun 토큰에서_사용자_역할을_가져온다() {
        val token = tokenProvider.generateAccessToken(1L, Role.SELLER, SESSION_ID)

        val role = tokenProvider.getUserRoleFromToken(token)

        assertThat(role).isEqualTo(Role.SELLER)
    }

    @Test
    fun 토큰에서_UserPrincipal을_가져온다() {
        val token = tokenProvider.generateAccessToken(5L, Role.BUYER, SESSION_ID)

        val principal = tokenProvider.getUserPrincipal(token)

        assertThat(principal.getUserId()).isEqualTo(5L)
    }

    @Test
    fun 토큰의_남은_만료_시간을_가져온다() {
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER, SESSION_ID)

        val remaining = tokenProvider.getRemainingExpirationMs(token)

        assertThat(remaining).isPositive()
    }

    @Test
    fun 액세스_토큰에_밀리초_발급_시각을_싣는다() {
        val before = System.currentTimeMillis()
        val token = tokenProvider.generateAccessToken(1L, Role.BUYER, SESSION_ID)
        val after = System.currentTimeMillis()

        val claims = tokenProvider.parseClaims(token)
        val issuedAtMs = (claims["iat_ms"] as Number).toLong()
        assertThat(issuedAtMs).isBetween(before, after)
        assertThat(issuedAtMs / 1000).isEqualTo(claims.issuedAt.time / 1000)
    }

    companion object {
        private const val SESSION_ID = "11111111-1111-1111-1111-111111111111"
        private val ISSUED_AT: Instant = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS)
        private val EXPIRES_AT: Instant = ISSUED_AT.plus(Duration.ofDays(1))
    }
}
