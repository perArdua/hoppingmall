package com.hoppingmall.user.config.ratelimit

import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.TokenProviderImpl
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.service.AuthServiceImpl
import com.hoppingmall.user.auth.service.IssuedTokenInvalidator
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.controller.UserController
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.service.UserQueryServiceImpl
import com.hoppingmall.user.support.RedisContainerSupport
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.issuingRefreshTokenService
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.redisson.api.RedissonClient
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@DisplayName("로그인 실패 제한의 (이메일, IP) 쌍과 이메일 단독 잠금 — 실제 Redis")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LoginAttemptLockPerIpRedisIntegrationTest {

    private val passwordEncoder = BCryptPasswordEncoder(4)

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        redissonClient.keys.flushall()

        val userRepository = mock<UserRepository>()
        val member = User.fixture(
            email = Email(MEMBER_EMAIL),
            password = Password(passwordEncoder.encode(CORRECT_PASSWORD))
        ).withId(1L)
        whenever(userRepository.findByEmail(Email(MEMBER_EMAIL))).thenReturn(member)

        val jwtProperties = JwtProperties().apply {
            secret = "test-secret-key-for-testing-purposes-only-32chars!"
            accessExpirationMs = 3600000
            refreshExpirationMs = 86400000
        }
        val authService = AuthServiceImpl(
            userQueryService = UserQueryServiceImpl(userRepository, PasswordVerifier(passwordEncoder)),
            tokenProvider = TokenProviderImpl(jwtProperties),
            refreshTokenService = issuingRefreshTokenService(),
            userRepository = userRepository,
            accessTokenBlacklistRepository = mock<AccessTokenBlacklistRepository>(),
            loginAttemptLimiter = RedisLoginAttemptLimiter(redissonClient, Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)),
            issuedTokenInvalidator = mock<IssuedTokenInvalidator>()
        )
        mockMvc = MockMvcBuilders.standaloneSetup(UserController(mock<UserCommandService>(), mock(), authService, ClientIpResolver()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun 남이_한_IP에서_10번_틀려도_계정_주인은_자기_IP에서_맞는_비밀번호로_로그인된다() {
        val attacker = (1..10).map { login(MEMBER_EMAIL, WRONG_PASSWORD, ATTACKER_IP) }

        val owner = login(MEMBER_EMAIL, CORRECT_PASSWORD, OWNER_IP)

        assertThat(attacker).containsOnly(401)
        assertThat(owner).isEqualTo(200)
    }

    @Test
    fun 한_IP에서_같은_이메일로_10번_틀리면_그_IP에서는_맞는_비밀번호도_429로_거절된다() {
        val failures = (1..10).map { login(MEMBER_EMAIL, WRONG_PASSWORD, ATTACKER_IP) }

        val sameIp = login(MEMBER_EMAIL, CORRECT_PASSWORD, ATTACKER_IP)

        assertThat(failures).containsOnly(401)
        assertThat(sameIp).isEqualTo(429)
    }

    @Test
    fun 여러_IP에서_합쳐_49번_틀려도_계정_주인은_로그인된다() {
        val failures = (1..49).map { attempt -> login(MEMBER_EMAIL, WRONG_PASSWORD, attackerIp(attempt % 7)) }

        val owner = login(MEMBER_EMAIL, CORRECT_PASSWORD, OWNER_IP)

        assertThat(failures).containsOnly(401)
        assertThat(owner).isEqualTo(200)
    }

    @Test
    fun 여러_IP에서_합쳐_50번_틀리면_어느_IP에서도_맞는_비밀번호가_429로_거절된다() {
        val failures = (1..50).map { attempt -> login(MEMBER_EMAIL, WRONG_PASSWORD, attackerIp(attempt % 7)) }

        val owner = login(MEMBER_EMAIL, CORRECT_PASSWORD, OWNER_IP)

        assertThat(failures).containsOnly(401)
        assertThat(owner).isEqualTo(429)
    }

    @Test
    fun 없는_이메일도_한_IP에서_10번_틀린_뒤_다른_IP에서는_429가_아니라_401로_답한다() {
        repeat(10) { login(MISSING_EMAIL, WRONG_PASSWORD, ATTACKER_IP) }

        val sameIp = login(MISSING_EMAIL, WRONG_PASSWORD, ATTACKER_IP)
        val otherIp = login(MISSING_EMAIL, WRONG_PASSWORD, OWNER_IP)

        assertThat(sameIp).isEqualTo(429)
        assertThat(otherIp).isEqualTo(401)
    }

    private fun attackerIp(index: Int): String = "10.0.1.${index + 1}"

    private fun login(email: String, password: String, ip: String): Int =
        mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
                .with { it.remoteAddr = ip; it }
        ).andReturn().response.status

    companion object {
        private const val MEMBER_EMAIL = "member@example.com"
        private const val MISSING_EMAIL = "missing@example.com"
        private const val CORRECT_PASSWORD = "Password1234"
        private const val WRONG_PASSWORD = "WrongPassword1234"
        private const val ATTACKER_IP = "10.0.0.66"
        private const val OWNER_IP = "10.0.0.7"

        private lateinit var redissonClient: RedissonClient

        @BeforeAll
        @JvmStatic
        fun startRedis() {
            redissonClient = RedisContainerSupport.createClient()
        }

        @AfterAll
        @JvmStatic
        fun stopRedis() {
            redissonClient.shutdown()
        }
    }
}
