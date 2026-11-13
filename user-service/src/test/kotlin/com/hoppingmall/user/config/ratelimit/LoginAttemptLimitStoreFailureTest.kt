package com.hoppingmall.user.config.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.TokenProviderImpl
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.service.AuthServiceImpl
import com.hoppingmall.user.auth.service.RefreshTokenService
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.controller.UserController
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.service.UserQueryServiceImpl
import com.hoppingmall.user.support.InMemoryRateLimitBuckets
import io.github.bucket4j.distributed.proxy.ProxyManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer
import org.redisson.api.RedissonClient
import org.redisson.client.RedisConnectionException
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock

@DisplayName("계정 기준 로그인 제한 저장소 장애 때의 동작")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LoginAttemptLimitStoreFailureTest {

    private val objectMapper = ObjectMapper()
    private val storeDown = Answer<Any> { throw RedisConnectionException("Unable to connect to Redis server") }

    @Test
    fun 계정_제한_저장소가_죽으면_IP_제한_저장소가_죽었을_때와_같은_응답으로_답한다() {
        val ipStoreDown = loginWith(
            proxyManager = mock<ProxyManager<String>>(defaultAnswer = storeDown),
            loginAttemptLimiter = NoOpLoginAttemptLimiter()
        )
        val accountStoreDown = loginWith(
            proxyManager = InMemoryRateLimitBuckets().proxyManager(),
            loginAttemptLimiter = RedisLoginAttemptLimiter(mock<RedissonClient>(defaultAnswer = storeDown), Clock.systemUTC())
        )

        assertThat(ipStoreDown.first).isEqualTo(500)
        assertThat(accountStoreDown).isEqualTo(ipStoreDown)
    }

    private fun loginWith(proxyManager: ProxyManager<String>, loginAttemptLimiter: LoginAttemptLimiter): Triple<Int, String, String> {
        val jwtProperties = JwtProperties().apply {
            secret = "test-secret-key-for-testing-purposes-only-32chars!"
            accessExpirationMs = 3600000
            refreshExpirationMs = 86400000
        }
        val userRepository = mock<UserRepository>()
        val authService = AuthServiceImpl(
            userQueryService = UserQueryServiceImpl(userRepository, PasswordVerifier(BCryptPasswordEncoder(4))),
            tokenProvider = TokenProviderImpl(jwtProperties),
            refreshTokenService = mock<RefreshTokenService>(),
            jwtProperties = jwtProperties,
            userRepository = userRepository,
            accessTokenBlacklistRepository = mock<AccessTokenBlacklistRepository>(),
            loginAttemptLimiter = loginAttemptLimiter
        )
        val mockMvc = MockMvcBuilders.standaloneSetup(UserController(mock<UserCommandService>(), mock(), authService))
            .addInterceptors(RateLimitInterceptor(proxyManager))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        val response = mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"member@example.com","password":"WrongPassword1234"}""")
                .with { it.remoteAddr = "10.0.0.5"; it }
        ).andReturn().response
        val body = objectMapper.readTree(response.getContentAsString(Charsets.UTF_8))
        return Triple(response.status, body.path("code").asText(), body.path("message").asText())
    }
}
