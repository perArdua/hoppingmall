package com.hoppingmall.user.config.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
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

@DisplayName("계정(이메일) 기준 로그인 실패 제한 — 실제 Redis")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LoginAttemptLimitRedisIntegrationTest {

    private val passwordEncoder = BCryptPasswordEncoder(4)
    private val objectMapper = ObjectMapper()
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
    fun IP를_바꿔_가며_같은_이메일로_10번_틀리면_맞는_비밀번호도_429로_거절된다() {
        val failures = (1..10).map { login(MEMBER_EMAIL, WRONG_PASSWORD).status }

        val afterLimit = login(MEMBER_EMAIL, CORRECT_PASSWORD)

        assertThat(failures).containsOnly(401)
        assertThat(afterLimit.status).isEqualTo(429)
    }

    @Test
    fun 없는_이메일도_10번_실패하면_있는_이메일과_같은_응답으로_거절된다() {
        repeat(10) { login(MEMBER_EMAIL, WRONG_PASSWORD) }
        repeat(10) { login(MISSING_EMAIL, WRONG_PASSWORD) }

        val member = login(MEMBER_EMAIL, WRONG_PASSWORD)
        val missing = login(MISSING_EMAIL, WRONG_PASSWORD)

        assertThat(missing.status).isEqualTo(429)
        assertThat(member).isEqualTo(missing)
    }

    @Test
    fun 실패가_9번이면_맞는_비밀번호로_로그인되고_실패_횟수가_초기화된다() {
        repeat(9) { login(MEMBER_EMAIL, WRONG_PASSWORD) }

        val success = login(MEMBER_EMAIL, CORRECT_PASSWORD)
        val failuresAfterReset = (1..9).map { login(MEMBER_EMAIL, WRONG_PASSWORD).status }

        assertThat(success.status).isEqualTo(200)
        assertThat(failuresAfterReset).containsOnly(401)
        assertThat(login(MEMBER_EMAIL, CORRECT_PASSWORD).status).isEqualTo(200)
    }

    @Test
    fun 거절은_10번째_실패로부터_15분_동안이고_그_뒤에는_다시_로그인된다() {
        repeat(10) { login(MEMBER_EMAIL, WRONG_PASSWORD) }

        clock.advance(Duration.ofMinutes(15).minusSeconds(1))
        val justBefore = login(MEMBER_EMAIL, CORRECT_PASSWORD)
        clock.advance(Duration.ofSeconds(1))
        val after = login(MEMBER_EMAIL, CORRECT_PASSWORD)

        assertThat(justBefore.status).isEqualTo(429)
        assertThat(after.status).isEqualTo(200)
    }

    @Test
    fun 실패한_지_15분이_지나면_그_실패는_세지_않는다() {
        repeat(9) { login(MEMBER_EMAIL, WRONG_PASSWORD) }
        clock.advance(Duration.ofMinutes(15))

        val tenthFailure = login(MEMBER_EMAIL, WRONG_PASSWORD)
        val next = login(MEMBER_EMAIL, CORRECT_PASSWORD)

        assertThat(tenthFailure.status).isEqualTo(401)
        assertThat(next.status).isEqualTo(200)
    }

    @Test
    fun 대소문자만_바꾼_이메일도_같은_이메일로_센다() {
        val variants = listOf("Member@example.com", "MEMBER@EXAMPLE.COM", "member@Example.com", "mEmber@example.com", "membeR@example.COM")
        repeat(10) { attempt -> login(variants[attempt % variants.size], WRONG_PASSWORD) }

        val afterLimit = login(MEMBER_EMAIL, CORRECT_PASSWORD)

        assertThat(afterLimit.status).isEqualTo(429)
    }

    @Test
    fun 다른_이메일의_실패는_서로의_횟수에_더해지지_않는다() {
        repeat(10) { login(MISSING_EMAIL, WRONG_PASSWORD) }

        val other = login(MEMBER_EMAIL, CORRECT_PASSWORD)

        assertThat(other.status).isEqualTo(200)
    }

    private fun login(email: String, password: String): LoginResult {
        val ip = ipSequence.incrementAndGet()
        val response = mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}""")
                .with { it.remoteAddr = "10.${ip / 65536 % 256}.${ip / 256 % 256}.${ip % 256}"; it }
        ).andReturn().response
        val body = objectMapper.readTree(response.getContentAsString(Charsets.UTF_8))
        return LoginResult(response.status, body.path("code").asText(), body.path("message").asText())
    }

    private data class LoginResult(val status: Int, val code: String, val message: String)

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
