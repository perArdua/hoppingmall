package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.SessionProperties
import com.hoppingmall.user.auth.TokenProviderImpl
import com.hoppingmall.user.auth.domain.SessionRevokeReason
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.user.auth.exception.InvalidTokenException
import com.hoppingmall.user.config.ratelimit.NoOpLoginAttemptLimiter
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.response.SignInResponse
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

@ExtendWith(MockitoExtension::class)
@DisplayName("리프레시 토큰으로 로그아웃")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LogoutWithRefreshTokenTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userQueryService: UserQueryService

    @Mock
    private lateinit var legacyRefreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var accessTokenBlacklistRepository: AccessTokenBlacklistRepository

    private val sessions = InMemoryUserSessions()

    private val jwtProperties = JwtProperties().apply {
        secret = SECRET
        accessExpirationMs = 3600000
        refreshExpirationMs = 86400000
    }

    private val tokenProvider = TokenProviderImpl(jwtProperties)

    private lateinit var authService: AuthServiceImpl

    private val user = User.fixture().withId(USER_ID)

    @BeforeEach
    fun setUp() {
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
            loginAttemptLimiter = NoOpLoginAttemptLimiter(),
            issuedTokenInvalidator = mock<IssuedTokenInvalidator>()
        )
        lenient().`when`(userRepository.findForUpdateById(USER_ID)).thenReturn(user)
    }

    @Test
    fun 만료된_액세스_토큰과_리프레시_토큰으로_로그아웃하면_그_세션만_끊긴다() {
        val loggingOut = login()
        val other = login()
        val expiredAccess = expiredAccessToken(sessionOf(loggingOut))

        authService.logout(expiredAccess, loggingOut.refreshToken)

        assertThat(revokeReasonOf(loggingOut)).isEqualTo(SessionRevokeReason.LOGOUT)
        assertThat(revokeReasonOf(other)).isNull()
        verify(accessTokenBlacklistRepository, never()).add(any(), any())
    }

    @Test
    fun 액세스_토큰_없이_리프레시_토큰만으로_로그아웃된다() {
        val signIn = login()

        authService.logout(null, signIn.refreshToken)

        assertThat(revokeReasonOf(signIn)).isEqualTo(SessionRevokeReason.LOGOUT)
    }

    @Test
    fun 유효한_액세스_토큰이_함께_오면_그_토큰도_블랙리스트에_올린다() {
        val signIn = login()

        authService.logout(signIn.accessToken, signIn.refreshToken)

        verify(accessTokenBlacklistRepository).add(eq(signIn.accessToken), any())
        assertThat(revokeReasonOf(signIn)).isEqualTo(SessionRevokeReason.LOGOUT)
    }

    @Test
    fun 이미_끊긴_세션의_리프레시_토큰으로_다시_로그아웃해도_성공한다() {
        val signIn = login()
        authService.logout(null, signIn.refreshToken)

        authService.logout(null, signIn.refreshToken)

        assertThat(revokeReasonOf(signIn)).isEqualTo(SessionRevokeReason.LOGOUT)
    }

    @Test
    fun 토큰이_둘_다_없으면_A003으로_거절한다() {
        assertThatThrownBy { authService.logout(null, null) }.isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun 만료된_액세스_토큰만_오면_A003으로_거절한다() {
        val signIn = login()

        assertThatThrownBy { authService.logout(expiredAccessToken(sessionOf(signIn)), null) }
            .isInstanceOf(InvalidTokenException::class.java)
        assertThat(revokeReasonOf(signIn)).isNull()
    }

    @Test
    fun 서명이_틀린_리프레시_토큰이면_A003으로_거절하고_세션은_그대로다() {
        val signIn = login()
        val forged = signIn.refreshToken.dropLast(2) + "xx"

        assertThatThrownBy { authService.logout(null, forged) }.isInstanceOf(InvalidTokenException::class.java)
        assertThat(revokeReasonOf(signIn)).isNull()
    }

    private fun login(): SignInResponse {
        val request = SignInRequest(email = "user@example.com", password = "Password1234")
        whenever(userQueryService.authenticate(request)).thenReturn(user)
        return authService.login(request, "10.0.0.1")
    }

    private fun sessionOf(signIn: SignInResponse): String = tokenProvider.getSessionId(signIn.accessToken)!!

    private fun revokeReasonOf(signIn: SignInResponse): SessionRevokeReason? =
        sessions.sessions.single { it.sessionId == sessionOf(signIn) }.revokeReason

    private fun expiredAccessToken(sessionId: String): String =
        Jwts.builder()
            .setSubject(USER_ID.toString())
            .claim("typ", "access")
            .claim("role", "BUYER")
            .claim("sid", sessionId)
            .setIssuedAt(Date(System.currentTimeMillis() - 7_200_000))
            .setExpiration(Date(System.currentTimeMillis() - 3_600_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    companion object {
        private const val USER_ID = 1L
        private const val SECRET = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256"
    }
}
