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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("AuthServiceImpl 단위 테스트")
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
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    private lateinit var authService: AuthServiceImpl

    @BeforeEach
    fun setUp() {
        lenient().whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)

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
            accessTokenBlacklistRepository = AccessTokenBlacklistRepository(redisTemplate)
        )
    }

    @Test
    fun 로그인_성공_시_accessToken과_refreshToken을_발급하고_refreshToken을_회전한다() {
        val request = SignInRequest(email = "seller@example.com", password = "Password1234")
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.SELLER).withId(1L)
        whenever(userQueryService.authenticate(request)).thenReturn(user)
        whenever(tokenProvider.generateAccessToken(1L, Role.SELLER)).thenReturn("access-token")
        whenever(tokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token")

        val response = authService.login(request)

        assertEquals("access-token", response.accessToken)
        assertEquals("refresh-token", response.refreshToken)
        verify(refreshTokenService).rotateRefreshToken(1L, "refresh-token", 86400000)
    }

    @Test
    fun 리프레시_토큰_재발급_성공_시_새로운_accessToken과_refreshToken을_반환한다() {
        val user = com.hoppingmall.user.domain.User.fixture(role = Role.BUYER).withId(10L)
        whenever(tokenProvider.parseRefreshToken("refresh-token")).thenReturn(10L)
        whenever(userRepository.findById(10L)).thenReturn(Optional.of(user))
        whenever(tokenProvider.generateAccessToken(10L, Role.BUYER)).thenReturn("new-access-token")
        whenever(tokenProvider.generateRefreshToken(10L)).thenReturn("new-refresh-token")

        val response: TokenRefreshResponse = authService.refreshAccessToken("refresh-token")

        assertEquals("new-access-token", response.accessToken)
        assertEquals("new-refresh-token", response.refreshToken)
        verify(refreshTokenService).validate(10L, "refresh-token")
        verify(refreshTokenService).rotateRefreshToken(10L, "new-refresh-token", 86400000)
    }

    @Test
    fun 리프레시_토큰에_해당하는_사용자가_없으면_예외가_발생한다() {
        whenever(tokenProvider.parseRefreshToken("missing-user-token")).thenReturn(999L)
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<UserNotFoundException> {
            authService.refreshAccessToken("missing-user-token")
        }
    }

    @Test
    fun 로그아웃_시_accessToken을_blacklist에_등록하고_refreshToken을_삭제한다() {
        whenever(tokenProvider.parseAccessToken("access-token")).thenReturn(7L)
        whenever(tokenProvider.getRemainingExpirationMs("access-token")).thenReturn(1000L)

        authService.logout("access-token")

        verify(valueOperations).set("blacklist:access-token", "blacklisted", 1000L, TimeUnit.MILLISECONDS)
        verify(refreshTokenService).delete(7L)
    }
}
