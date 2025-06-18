package com.hoppingmall.mall.global.auth.service

import com.hoppingmall.mall.global.auth.dto.response.TokenRefreshResponse
import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.jwt.JwtProperties
import com.hoppingmall.mall.global.jwt.TokenProvider
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.Buyer
import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.response.user.SignInResponse
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import com.hoppingmall.mall.user.service.user.UserQueryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

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

    @Test
    fun `로그인에 성공하면 액세스 토큰과 리프레시 토큰을 발급한다`() {
        // given
        val request = SignInRequest("email@example.com", "password123")
        val user = User.fixture(role = Role.SELLER).withId(1L)

        whenever(userQueryService.authenticate(request)).thenReturn(user)
        whenever(tokenProvider.generateAccessToken(1L, Role.SELLER)).thenReturn("access-token")
        whenever(tokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token")

        // when
        val response: SignInResponse = authService.login(request)

        // then
        verify(refreshTokenService).rotateRefreshToken(1L, "refresh-token", jwtProperties.refreshExpirationMs)
        assert(response.accessToken == "access-token")
        assert(response.refreshToken == "refresh-token")
    }

    @Test
    fun `리프레시 토큰이 유효하면 새로운 액세스 토큰과 리프레시 토큰을 재발급한다`() {
        // given
        val refreshToken = "valid-refresh-token"
        val user = User.fixture().withId(10L)

        whenever(tokenProvider.parseRefreshToken(refreshToken)).thenReturn(10L)
        whenever(userRepository.findById(10L)).thenReturn(Optional.of(user))
        whenever(tokenProvider.generateAccessToken(10L, Role.BUYER)).thenReturn("new-access-token")
        whenever(tokenProvider.generateRefreshToken(10L)).thenReturn("new-refresh-token")

        // when
        val result: TokenRefreshResponse = authService.refreshAccessToken(refreshToken)

        // then
        verify(refreshTokenService).validate(10L, refreshToken)
        verify(refreshTokenService).rotateRefreshToken(10L, "new-refresh-token", jwtProperties.refreshExpirationMs)
        assert(result.accessToken == "new-access-token")
        assert(result.refreshToken == "new-refresh-token")
    }

    @Test
    fun `리프레시 토큰으로 사용자를 찾을 수 없으면 예외가 발생한다`() {
        // given
        val refreshToken = "unknown-refresh"
        whenever(tokenProvider.parseRefreshToken(refreshToken)).thenReturn(42L)
        whenever(userRepository.findById(42L)).thenReturn(Optional.empty())

        // when & then
        assertThrows<UserNotFoundException> {
            authService.refreshAccessToken(refreshToken)
        }
    }

    @Test
    fun `로그아웃 시 액세스 토큰으로 userId를 파싱하고 리프레시 토큰을 삭제한다`() {
        // given
        val accessToken = "valid-access-token"
        whenever(tokenProvider.parseAccessToken(accessToken)).thenReturn(7L)

        // when
        authService.logout(accessToken)

        // then
        verify(refreshTokenService).delete(7L)
    }
}
