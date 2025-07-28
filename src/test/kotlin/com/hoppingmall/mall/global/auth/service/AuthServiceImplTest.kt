package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.dto.response.TokenRefreshResponse
import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.jwt.JwtProperties
import com.hoppingmall.mall.global.jwt.TokenProvider
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import com.hoppingmall.mall.user.service.user.UserQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import java.util.*

@DisplayName("AuthServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AuthServiceImplTest {

    private val userQueryService: UserQueryService = mock()
    private val tokenProvider: TokenProvider = mock()
    private val refreshTokenService: RefreshTokenService = mock()
    private val jwtProperties: JwtProperties = JwtProperties().apply {
        secret = "secret-key".repeat(10)
        accessExpirationMs = 3600L
        refreshExpirationMs = 86400000L
    }
    private val userRepository: UserRepository = mock()
    private lateinit var authService: AuthServiceImpl

    @BeforeEach
    fun setUp() {
        authService = AuthServiceImpl(
            userQueryService,
            tokenProvider,
            refreshTokenService,
            jwtProperties,
            userRepository
        )
    }

    @Nested
    @DisplayName("로그인")
    inner class Login {
        @Test
        fun 성공하면_accessToken과_refreshToken을_발급한다() {
            val request = SignInRequest("email@example.com", "password123")
            val user = User.fixture(role = Role.SELLER).withId(1L)
            whenever(userQueryService.authenticate(request)).thenReturn(user)
            whenever(tokenProvider.generateAccessToken(1L, Role.SELLER)).thenReturn("access-token")
            whenever(tokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token")

            val response: SignInResponse = authService.login(request)

            verify(refreshTokenService).rotateRefreshToken(1L, "refresh-token", jwtProperties.refreshExpirationMs)
            assert(response.accessToken == "access-token")
            assert(response.refreshToken == "refresh-token")
        }
    }

    @Nested
    @DisplayName("refreshToken 재발급")
    inner class RefreshAccessToken {
        @Test
        fun 유효한_refreshToken이면_새로운_토큰을_재발급한다() {
            val refreshToken = "valid-refresh-token"
            val user = User.fixture().withId(10L)
            whenever(tokenProvider.parseRefreshToken(refreshToken)).thenReturn(10L)
            whenever(userRepository.findById(10L)).thenReturn(Optional.of(user))
            whenever(tokenProvider.generateAccessToken(10L, Role.BUYER)).thenReturn("new-access-token")
            whenever(tokenProvider.generateRefreshToken(10L)).thenReturn("new-refresh-token")

            val result: TokenRefreshResponse = authService.refreshAccessToken(refreshToken)

            verify(refreshTokenService).validate(10L, refreshToken)
            verify(refreshTokenService).rotateRefreshToken(10L, "new-refresh-token", jwtProperties.refreshExpirationMs)
            assert(result.accessToken == "new-access-token")
            assert(result.refreshToken == "new-refresh-token")
        }

        @Test
        fun user를_찾을_수_없으면_예외가_발생한다() {
            val refreshToken = "unknown-refresh"
            whenever(tokenProvider.parseRefreshToken(refreshToken)).thenReturn(42L)
            whenever(userRepository.findById(42L)).thenReturn(Optional.empty())

            assertThrows<UserNotFoundException> {
                authService.refreshAccessToken(refreshToken)
            }
        }
    }

    @Nested
    @DisplayName("로그아웃")
    inner class Logout {
        @Test
        fun accessToken으로_userId를_파싱하고_refreshToken을_삭제한다() {
            val accessToken = "valid-access-token"
            whenever(tokenProvider.parseAccessToken(accessToken)).thenReturn(7L)

            authService.logout(accessToken)

            verify(refreshTokenService).delete(7L)
        }
    }
}
