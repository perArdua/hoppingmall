package com.hoppingmall.user.auth

import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.support.MySqlContainerSupport
import com.hoppingmall.user.support.RedisContainerSupport
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@DisplayName("비밀번호 변경·전체 로그아웃 때 이전 액세스 토큰 무효 시각 기록 — 실제 MySQL·Redis")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AccessTokenInvalidationMySqlRedisIntegrationTest {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var userCommandService: UserCommandService

    @Autowired
    @Qualifier("stringRedisTemplate")
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    @Value("\${jwt.secret}")
    private lateinit var secret: String

    @Test
    fun 비밀번호를_바꾸면_그_전에_나간_액세스_토큰보다_늦은_무효_시각이_액세스_토큰_수명_이상_남는다() {
        val account = newAccount()
        val beforeChange = authService.login(account.signIn, "10.0.0.1")
        val changeStartedAt = System.currentTimeMillis()

        userCommandService.updateUserProfile(
            account.userId,
            UpdateUserRequest(name = "새이름", password = NEW_PASSWORD, currentPassword = PASSWORD)
        )
        val changeFinishedAt = System.currentTimeMillis()

        val invalidBefore = redisTemplate.opsForValue().get(keyOf(account.userId))?.toLong()
        val ttlMs = redisTemplate.getExpire(keyOf(account.userId), TimeUnit.MILLISECONDS)
        assertThat(invalidBefore).isNotNull().isBetween(changeStartedAt, changeFinishedAt)
        assertThat(issuedAtMs(beforeChange.accessToken)).isLessThan(invalidBefore)
        assertThat(ttlMs).isGreaterThanOrEqualTo(jwtProperties.accessExpirationMs)
    }

    @Test
    fun 비밀번호를_바꾼_뒤_다시_로그인한_액세스_토큰은_무효_시각보다_늦게_나간다() {
        val account = newAccount()
        userCommandService.updateUserProfile(
            account.userId,
            UpdateUserRequest(name = "새이름", password = NEW_PASSWORD, currentPassword = PASSWORD)
        )

        val afterChange = authService.login(SignInRequest(account.signIn.email, NEW_PASSWORD), "10.0.0.1")

        val invalidBefore = redisTemplate.opsForValue().get(keyOf(account.userId))!!.toLong()
        assertThat(issuedAtMs(afterChange.accessToken)).isGreaterThanOrEqualTo(invalidBefore)
    }

    @Test
    fun 이름만_바꾸면_무효_시각을_쓰지_않는다() {
        val account = newAccount()

        userCommandService.updateUserProfile(account.userId, UpdateUserRequest(name = "새이름"))

        assertThat(redisTemplate.hasKey(keyOf(account.userId))).isFalse()
    }

    @Test
    fun 전체_로그아웃하면_모든_세션이_끊기고_무효_시각을_쓴다() {
        val account = newAccount()
        val firstDevice = authService.login(account.signIn, "10.0.0.1")
        val secondDevice = authService.login(account.signIn, "10.0.0.2")

        authService.logoutAll(account.userId, firstDevice.accessToken)

        val invalidBefore = redisTemplate.opsForValue().get(keyOf(account.userId))?.toLong()
        assertThat(invalidBefore).isNotNull()
        assertThat(issuedAtMs(secondDevice.accessToken)).isLessThan(invalidBefore)
        assertThat(runCatching { authService.refreshAccessToken(firstDevice.refreshToken) }.isFailure).isTrue()
        assertThat(runCatching { authService.refreshAccessToken(secondDevice.refreshToken) }.isFailure).isTrue()
        assertThat(redisTemplate.hasKey("blacklist:${firstDevice.accessToken}")).isTrue()
    }

    private fun issuedAtMs(accessToken: String): Long =
        (Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(secret.toByteArray()))
            .build()
            .parseClaimsJws(accessToken)
            .body["iat_ms"] as Number).toLong()

    private fun keyOf(userId: Long) = "token-invalid-before:$userId"

    private fun newAccount(): Account {
        val email = "invalidate-${UUID.randomUUID().toString().take(12)}@example.com"
        val signUp = userCommandService.signUp(SignUpRequest(email = email, password = PASSWORD, name = "무효화사용자"))
        return Account(signUp.id, SignInRequest(email = email, password = PASSWORD))
    }

    private data class Account(val userId: Long, val signIn: SignInRequest)

    companion object {
        private const val PASSWORD = "Password1234"
        private const val NEW_PASSWORD = "NewPassword1234"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "user_invalidation_test")
            registry.add("spring.data.redis.host") { RedisContainerSupport.host }
            registry.add("spring.data.redis.port") { RedisContainerSupport.port }
        }
    }
}
