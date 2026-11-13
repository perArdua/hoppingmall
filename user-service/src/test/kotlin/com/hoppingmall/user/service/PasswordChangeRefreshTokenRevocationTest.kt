package com.hoppingmall.user.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.TokenProviderImpl
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import com.hoppingmall.user.auth.service.AuthServiceImpl
import com.hoppingmall.user.auth.service.RefreshTokenServiceImpl
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.PasswordPolicy
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
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
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@DisplayName("비밀번호 변경 뒤 기존 refresh token 무효화")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PasswordChangeRefreshTokenRevocationTest {

    @Mock
    private lateinit var stringRedisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userQueryService: UserQueryService

    @Mock
    private lateinit var userDomainService: UserDomainService

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    @Mock
    private lateinit var accessTokenBlacklistRepository: AccessTokenBlacklistRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var passwordPolicy: PasswordPolicy

    private val redisStore = mutableMapOf<String, String>()

    private lateinit var user: User

    private lateinit var authService: AuthServiceImpl

    private lateinit var userCommandService: UserCommandServiceImpl

    @BeforeEach
    fun setUp() {
        val jwtProperties = JwtProperties().apply {
            secret = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256"
            accessExpirationMs = 3600000
            refreshExpirationMs = 86400000
        }
        val refreshTokenService = RefreshTokenServiceImpl(RefreshTokenRepository(stringRedisTemplate))
        authService = AuthServiceImpl(
            userQueryService = userQueryService,
            tokenProvider = TokenProviderImpl(jwtProperties),
            refreshTokenService = refreshTokenService,
            jwtProperties = jwtProperties,
            userRepository = userRepository,
            accessTokenBlacklistRepository = accessTokenBlacklistRepository
        )
        userCommandService = UserCommandServiceImpl(
            userRepository = userRepository,
            passwordCreator = PasswordCreator(passwordEncoder, passwordPolicy),
            passwordVerifier = PasswordVerifier(passwordEncoder),
            userDomainService = userDomainService,
            membershipCommandService = membershipCommandService,
            refreshTokenService = refreshTokenService
        )
        user = User.fixture().withId(1L)

        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)
        doAnswer { redisStore[it.getArgument(0)] = it.getArgument(1) }
            .whenever(valueOperations).set(any(), any(), any<Long>(), any<TimeUnit>())
        whenever(valueOperations.get(any())).thenAnswer { redisStore[it.getArgument(0)] }
        whenever(stringRedisTemplate.delete(any<String>())).thenAnswer { redisStore.remove(it.getArgument<String>(0)) != null }
        whenever(userRepository.findNullableById(1L)).thenReturn(user)
        lenient().`when`(userRepository.findById(1L)).thenReturn(Optional.of(user))
    }

    @Test
    fun 비밀번호를_바꾸면_변경_전에_발급된_refresh_token으로_재발급할_수_없다() {
        val issuedBeforeChange = login().refreshToken
        whenever(passwordEncoder.matches("Password1234", "encoded-password")).thenReturn(true)
        whenever(passwordEncoder.encode("NewPassword1234")).thenReturn("encoded-new-password")

        userCommandService.updateUserProfile(1L, passwordChangeRequest())

        assertThatThrownBy { authService.refreshAccessToken(issuedBeforeChange) }
            .isInstanceOf(RefreshTokenNotFoundException::class.java)
    }

    @Test
    fun 이름만_바꾸면_기존_refresh_token으로_계속_재발급할_수_있다() {
        val issuedBeforeChange = login().refreshToken

        userCommandService.updateUserProfile(1L, UpdateUserRequest(name = "새이름", password = null))
        val response = authService.refreshAccessToken(issuedBeforeChange)

        assertThat(response.accessToken).isNotBlank()
        assertThat(response.refreshToken).isNotBlank()
    }

    @Test
    fun 비밀번호를_바꾼_뒤_다시_로그인하면_새_refresh_token으로_재발급할_수_있다() {
        login()
        whenever(passwordEncoder.matches("Password1234", "encoded-password")).thenReturn(true)
        whenever(passwordEncoder.encode("NewPassword1234")).thenReturn("encoded-new-password")

        userCommandService.updateUserProfile(1L, passwordChangeRequest())
        val issuedAfterChange = login().refreshToken
        val response = authService.refreshAccessToken(issuedAfterChange)

        assertThat(response.accessToken).isNotBlank()
    }

    private fun passwordChangeRequest() =
        UpdateUserRequest(name = "새이름", password = "NewPassword1234", currentPassword = "Password1234")

    private fun login() = SignInRequest(email = "user@example.com", password = "Password1234").let { request ->
        whenever(userQueryService.authenticate(request)).thenReturn(user)
        authService.login(request)
    }
}
