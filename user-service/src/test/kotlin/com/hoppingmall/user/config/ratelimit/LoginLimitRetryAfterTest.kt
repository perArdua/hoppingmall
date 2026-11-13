package com.hoppingmall.user.config.ratelimit

import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.TokenProviderImpl
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.service.AuthServiceImpl
import com.hoppingmall.user.auth.service.RefreshTokenService
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.controller.UserController
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.service.UserQueryServiceImpl
import com.hoppingmall.user.support.InMemoryRateLimitBuckets
import com.hoppingmall.user.support.RedisContainerSupport
import com.hoppingmall.user.support.fixture.fixture
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
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("로그인 제한 429의 Retry-After 헤더")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LoginLimitRetryAfterTest {

    private val passwordEncoder = BCryptPasswordEncoder(4)
    private val ipSequence = AtomicInteger()
    private val clock = MutableClock(Instant.parse("2026-11-14T00:00:00Z"))

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
            refreshTokenService = mock<RefreshTokenService>(),
            jwtProperties = jwtProperties,
            userRepository = userRepository,
            accessTokenBlacklistRepository = mock<AccessTokenBlacklistRepository>(),
            loginAttemptLimiter = RedisLoginAttemptLimiter(redissonClient, clock)
        )
        val controller = UserController(mock<UserCommandService>(), mock(), authService)

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addInterceptors(RateLimitInterceptor(InMemoryRateLimitBuckets().proxyManager()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun 이메일_기준_제한의_429는_거절이_풀릴_때까지_남은_초를_Retry_After로_알린다() {
        repeat(10) { login(MEMBER_EMAIL, WRONG_PASSWORD, nextIp()) }

        val rightAway = login(MEMBER_EMAIL, CORRECT_PASSWORD, nextIp())
        clock.advance(Duration.ofMinutes(5))
        val afterFiveMinutes = login(MEMBER_EMAIL, CORRECT_PASSWORD, nextIp())

        assertThat(rightAway.status).isEqualTo(429)
        assertThat(rightAway.getHeader("Retry-After")).isEqualTo("900")
        assertThat(afterFiveMinutes.status).isEqualTo(429)
        assertThat(afterFiveMinutes.getHeader("Retry-After")).isEqualTo("600")
    }

    @Test
    fun 이메일_기준_제한의_남은_시간이_1초보다_짧아도_Retry_After는_1초로_올려_알린다() {
        repeat(10) { login(MEMBER_EMAIL, WRONG_PASSWORD, nextIp()) }

        clock.advance(Duration.ofMinutes(15).minusMillis(300))
        val almostReleased = login(MEMBER_EMAIL, CORRECT_PASSWORD, nextIp())

        assertThat(almostReleased.status).isEqualTo(429)
        assertThat(almostReleased.getHeader("Retry-After")).isEqualTo("1")
    }

    @Test
    fun IP_기준_제한의_429도_Retry_After로_남은_초를_알린다() {
        val ip = nextIp()
        val allowed = (1..5).map { login(MISSING_EMAIL, WRONG_PASSWORD, ip) }

        val limited = login(MISSING_EMAIL, WRONG_PASSWORD, ip)

        assertThat(allowed.map { it.status }).containsOnly(401)
        assertThat(allowed.map { it.getHeader("Retry-After") }).containsOnlyNulls()
        assertThat(limited.status).isEqualTo(429)
        assertThat(limited.getHeader("Retry-After")!!.toLong()).isBetween(1L, 60L)
    }

    @Test
    fun 제한에_걸리지_않은_실패와_성공에는_Retry_After가_없다() {
        val failure = login(MEMBER_EMAIL, WRONG_PASSWORD, nextIp())
        val success = login(MEMBER_EMAIL, CORRECT_PASSWORD, nextIp())

        assertThat(failure.status).isEqualTo(401)
        assertThat(failure.getHeader("Retry-After")).isNull()
        assertThat(success.status).isEqualTo(200)
        assertThat(success.getHeader("Retry-After")).isNull()
    }

    private fun nextIp(): String {
        val ip = ipSequence.incrementAndGet()
        return "10.${ip / 65536 % 256}.${ip / 256 % 256}.${ip % 256}"
    }

    private fun login(email: String, password: String, ip: String): MockHttpServletResponse {
        return mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
                .with { it.remoteAddr = ip; it }
        ).andReturn().response
    }

    private class MutableClock(private var instant: Instant) : Clock() {
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = instant
    }

    companion object {
        private const val MEMBER_EMAIL = "member@example.com"
        private const val MISSING_EMAIL = "missing@example.com"
        private const val CORRECT_PASSWORD = "Password1234"
        private const val WRONG_PASSWORD = "WrongPassword1234"

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
