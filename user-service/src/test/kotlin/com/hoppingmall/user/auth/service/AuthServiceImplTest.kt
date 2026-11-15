package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.TokenProvider
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.config.ratelimit.NoOpLoginAttemptLimiter
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.exception.user.UserNotFoundException
import com.hoppingmall.user.service.UserQueryService
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@DisplayName("AuthServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AuthServiceImplTest {

    @Mock
    private lateinit var userQueryService: UserQueryService

    @Mock
    private lateinit var tokenProvider: TokenProvider

    @Mock
    private lateinit var refreshTokenService: RefreshTokenService

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var accessTokenBlacklistRepository: AccessTokenBlacklistRepository

    private lateinit var authService: AuthServiceImpl

    @BeforeEach
    fun setUp() {
        authService = AuthServiceImpl(
            userQueryService = userQueryService,
            tokenProvider = tokenProvider,
            refreshTokenService = refreshTokenService,
            userRepository = userRepository,
            accessTokenBlacklistRepository = accessTokenBlacklistRepository,
            loginAttemptLimiter = NoOpLoginAttemptLimiter()
        )
    }

    @Test
    fun 로그인_성공_시_새_세션의_리프레시_토큰과_그_세션의_액세스_토큰을_발급한다() {
        val request = SignInRequest(email = "seller@example.com", password = "Password1234")
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.SELLER).withId(1L)
        whenever(userQueryService.authenticate(request)).thenReturn(user)
        whenever(refreshTokenService.issue(1L)).thenReturn(IssuedRefreshToken(1L, "session-1", "refresh-token"))
        whenever(tokenProvider.generateAccessToken(1L, Role.SELLER, "session-1")).thenReturn("access-token")

        val response = authService.login(request, "10.0.0.1")

        assertThat(response.accessToken).isEqualTo("access-token")
        assertThat(response.refreshToken).isEqualTo("refresh-token")
    }

    @Test
    fun 리프레시_토큰_재발급_시_세션을_회전하고_새로운_토큰을_반환한다() {
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.BUYER).withId(10L)
        whenever(refreshTokenService.rotate("refresh-token")).thenReturn(IssuedRefreshToken(10L, "session-10", "new-refresh-token"))
        whenever(userRepository.findById(10L)).thenReturn(Optional.of(user))
        whenever(tokenProvider.generateAccessToken(10L, Role.BUYER, "session-10")).thenReturn("new-access-token")

        val response: TokenRefreshResponse = authService.refreshAccessToken("refresh-token")

        assertThat(response.accessToken).isEqualTo("new-access-token")
        assertThat(response.refreshToken).isEqualTo("new-refresh-token")
    }

    @Test
    fun 리프레시_토큰에_해당하는_사용자가_없으면_예외가_발생한다() {
        whenever(refreshTokenService.rotate("missing-user-token")).thenReturn(IssuedRefreshToken(999L, "session-999", "next"))
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { authService.refreshAccessToken("missing-user-token") }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun 로그아웃_시_블랙리스트_등록_및_그_세션만_끊는다() {
        whenever(tokenProvider.parseAccessToken("access-token")).thenReturn(7L)
        whenever(tokenProvider.getSessionId("access-token")).thenReturn("session-7")
        whenever(tokenProvider.getRemainingExpirationMs("access-token")).thenReturn(1000L)

        authService.logout("access-token")

        verify(accessTokenBlacklistRepository).add("access-token", 1000L)
        verify(refreshTokenService).revokeSession(7L, "session-7")
    }
}
