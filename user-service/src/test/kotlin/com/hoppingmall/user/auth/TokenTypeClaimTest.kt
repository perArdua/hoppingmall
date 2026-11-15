package com.hoppingmall.user.auth

import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import com.hoppingmall.user.auth.exception.InvalidTokenException
import com.hoppingmall.user.auth.service.AuthServiceImpl
import com.hoppingmall.user.auth.service.RefreshTokenServiceImpl
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.config.ratelimit.NoOpLoginAttemptLimiter
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.service.UserQueryService
import com.hoppingmall.user.support.InMemoryUserSessions
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@DisplayName("토큰 종류 클레임으로 access와 refresh 구분")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TokenTypeClaimTest {

    @Mock
    private lateinit var legacyRefreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userQueryService: UserQueryService

    @Mock
    private lateinit var accessTokenBlacklistRepository: AccessTokenBlacklistRepository

    private val secret = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256"

    private val sessions = InMemoryUserSessions()

    private lateinit var tokenProvider: TokenProviderImpl

    private lateinit var authService: AuthServiceImpl

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        val jwtProperties = JwtProperties().apply {
            secret = this@TokenTypeClaimTest.secret
            accessExpirationMs = 3600000
            refreshExpirationMs = 86400000
        }
        tokenProvider = TokenProviderImpl(jwtProperties)
        authService = AuthServiceImpl(
            userQueryService = userQueryService,
            tokenProvider = tokenProvider,
            refreshTokenService = RefreshTokenServiceImpl(
                userSessionRepository = sessions.repository,
                userRepository = userRepository,
                legacyRefreshTokenRepository = legacyRefreshTokenRepository,
                tokenProvider = tokenProvider,
                jwtProperties = jwtProperties,
                sessionProperties = SessionProperties()
            ),
            userRepository = userRepository,
            accessTokenBlacklistRepository = accessTokenBlacklistRepository,
            loginAttemptLimiter = NoOpLoginAttemptLimiter()
        )
        user = User.fixture().withId(1L)

        lenient().`when`(userRepository.findById(1L)).thenReturn(Optional.of(user))
        lenient().`when`(userRepository.findForUpdateById(1L)).thenReturn(user)
    }

    @Test
    fun access_token에는_typ_access가_refresh_token에는_typ_refresh가_들어간다() {
        val accessToken = tokenProvider.generateAccessToken(1L, Role.BUYER, SESSION_ID)
        val refreshToken = tokenProvider.generateRefreshToken(1L, SESSION_ID, 1L, ISSUED_AT, EXPIRES_AT)

        assertThat(tokenProvider.parseClaims(accessToken)["typ"]).isEqualTo("access")
        assertThat(tokenProvider.parseClaims(refreshToken)["typ"]).isEqualTo("refresh")
    }

    @Test
    fun access_token을_refresh_token으로_파싱하면_거절된다() {
        val accessToken = tokenProvider.generateAccessToken(1L, Role.BUYER, SESSION_ID)

        assertThatThrownBy { tokenProvider.parseRefreshClaims(accessToken) }
            .isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun refresh_token을_access_token으로_파싱하면_거절된다() {
        val refreshToken = tokenProvider.generateRefreshToken(1L, SESSION_ID, 1L, ISSUED_AT, EXPIRES_AT)

        assertThatThrownBy { tokenProvider.parseAccessToken(refreshToken) }
            .isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun 알_수_없는_typ은_access로도_refresh로도_받지_않는다() {
        val token = signedToken(typ = "id", role = Role.BUYER.name)

        assertThatThrownBy { tokenProvider.parseAccessToken(token) }
            .isInstanceOf(InvalidTokenException::class.java)
        assertThatThrownBy { tokenProvider.parseRefreshClaims(token) }
            .isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun access_token으로_재발급하면_거절되고_저장된_refresh_token은_그대로다() {
        val signIn = login()

        assertThatThrownBy { authService.refreshAccessToken(signIn.accessToken) }
            .isInstanceOf(InvalidTokenException::class.java)
        assertThat(sessions.sessions.single().generation).isEqualTo(1L)
        assertThat(authService.refreshAccessToken(signIn.refreshToken).refreshToken).isNotBlank()
    }

    @Test
    fun refresh_token으로_재발급하면_새_access와_refresh가_종류대로_나간다() {
        val signIn = login()

        val response = authService.refreshAccessToken(signIn.refreshToken)

        assertThat(tokenProvider.parseClaims(response.accessToken)["typ"]).isEqualTo("access")
        assertThat(tokenProvider.parseClaims(response.refreshToken)["typ"]).isEqualTo("refresh")
        assertThat(sessions.sessions.single().generation).isEqualTo(2L)
    }

    @Test
    fun refresh_token으로_로그아웃하면_거절되고_블랙리스트와_refresh_token이_그대로다() {
        val signIn = login()

        assertThatThrownBy { authService.logout(signIn.refreshToken) }
            .isInstanceOf(InvalidTokenException::class.java)
        verify(accessTokenBlacklistRepository, never()).add(any(), any())
        assertThat(sessions.sessions.single().revokedAt).isNull()
    }

    @Test
    fun access_token으로_로그아웃하면_블랙리스트에_올리고_그_세션을_끊는다() {
        val signIn = login()

        authService.logout(signIn.accessToken)

        verify(accessTokenBlacklistRepository).add(any(), any())
        assertThatThrownBy { authService.refreshAccessToken(signIn.refreshToken) }
            .isInstanceOf(RefreshTokenNotFoundException::class.java)
    }

    @Test
    fun typ이_없는_옛_refresh_token은_role이_없으므로_refresh로_받는다() {
        val legacyRefresh = signedToken(typ = null, role = null)

        assertThat(tokenProvider.parseRefreshClaims(legacyRefresh).userId).isEqualTo(1L)
        assertThatThrownBy { tokenProvider.parseAccessToken(legacyRefresh) }
            .isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun typ이_없는_옛_access_token은_role이_있으므로_refresh로_받지_않는다() {
        val legacyAccess = signedToken(typ = null, role = Role.BUYER.name)

        assertThatThrownBy { tokenProvider.parseRefreshClaims(legacyAccess) }
            .isInstanceOf(InvalidTokenException::class.java)
        assertThat(tokenProvider.parseAccessToken(legacyAccess)).isEqualTo(1L)
    }

    @Test
    fun typ이_없는_옛_refresh_token으로도_재발급할_수_있다() {
        val legacyRefresh = signedToken(typ = null, role = null)
        whenever(legacyRefreshTokenRepository.findByUserId(1L)).thenReturn(legacyRefresh)

        val response = authService.refreshAccessToken(legacyRefresh)

        assertThat(tokenProvider.parseClaims(response.refreshToken)["typ"]).isEqualTo("refresh")
        verify(legacyRefreshTokenRepository).deleteByUserId(1L)
    }

    private fun login() = SignInRequest(email = "user@example.com", password = "Password1234").let { request ->
        whenever(userQueryService.authenticate(request)).thenReturn(user)
        authService.login(request, "10.0.0.1")
    }

    private fun signedToken(typ: String?, role: String?): String {
        val now = Date()
        val builder = Jwts.builder()
            .setSubject("1")
            .setIssuedAt(now)
            .setExpiration(Date(now.time + 60000))
        typ?.let { builder.claim("typ", it) }
        role?.let { builder.claim("role", it) }
        return builder
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray()), SignatureAlgorithm.HS256)
            .compact()
    }

    companion object {
        private const val SESSION_ID = "11111111-1111-1111-1111-111111111111"
        private val ISSUED_AT: Instant = Instant.ofEpochSecond(Instant.now().epochSecond)
        private val EXPIRES_AT: Instant = ISSUED_AT.plus(Duration.ofDays(1))
    }
}
