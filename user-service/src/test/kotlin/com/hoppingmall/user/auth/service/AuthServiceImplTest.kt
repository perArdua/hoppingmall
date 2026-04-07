package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.TokenProvider
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.common.enums.Role
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
            jwtProperties = JwtProperties().apply {
                secret = "test-secret-key-for-testing-purposes-only-32chars!"
                accessExpirationMs = 3600000
                refreshExpirationMs = 86400000
            },
            userRepository = userRepository,
            accessTokenBlacklistRepository = accessTokenBlacklistRepository
        )
    }

    @Test
    fun 로그인_성공_시_토큰을_발급하고_리프레시_토큰을_회전한다() {
        val request = SignInRequest(email = "seller@example.com", password = "Password1234")
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.SELLER).withId(1L)
        whenever(userQueryService.authenticate(request)).thenReturn(user)
        whenever(tokenProvider.generateAccessToken(1L, Role.SELLER)).thenReturn("access-token")
        whenever(tokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token")

        val response = authService.login(request)

        assertThat(response.accessToken).isEqualTo("access-token")
        assertThat(response.refreshToken).isEqualTo("refresh-token")
        verify(refreshTokenService).rotateRefreshToken(1L, "refresh-token", 86400000)
    }

    @Test
    fun 리프레시_토큰_재발급_시_새로운_토큰을_반환한다() {
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.BUYER).withId(10L)
        whenever(tokenProvider.parseRefreshToken("refresh-token")).thenReturn(10L)
        whenever(userRepository.findById(10L)).thenReturn(Optional.of(user))
        whenever(tokenProvider.generateAccessToken(10L, Role.BUYER)).thenReturn("new-access-token")
        whenever(tokenProvider.generateRefreshToken(10L)).thenReturn("new-refresh-token")

        val response: TokenRefreshResponse = authService.refreshAccessToken("refresh-token")

        assertThat(response.accessToken).isEqualTo("new-access-token")
        assertThat(response.refreshToken).isEqualTo("new-refresh-token")
        verify(refreshTokenService).validate(10L, "refresh-token")
        verify(refreshTokenService).rotateRefreshToken(10L, "new-refresh-token", 86400000)
    }

    @Test
    fun 리프레시_토큰에_해당하는_사용자가_없으면_예외가_발생한다() {
        whenever(tokenProvider.parseRefreshToken("missing-user-token")).thenReturn(999L)
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { authService.refreshAccessToken("missing-user-token") }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun 로그아웃_시_블랙리스트_등록_및_리프레시_토큰_삭제() {
        whenever(tokenProvider.parseAccessToken("access-token")).thenReturn(7L)
        whenever(tokenProvider.getRemainingExpirationMs("access-token")).thenReturn(1000L)

        authService.logout("access-token")

        verify(accessTokenBlacklistRepository).add("access-token", 1000L)
        verify(refreshTokenService).delete(7L)
    }
}
