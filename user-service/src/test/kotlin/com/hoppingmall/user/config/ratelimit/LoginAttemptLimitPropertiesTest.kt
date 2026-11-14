package com.hoppingmall.user.config.ratelimit

import com.hoppingmall.user.support.RedisContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.redisson.api.RedissonClient
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@DisplayName("로그인 실패 제한 프로퍼티")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LoginAttemptLimitPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(RedissonClient::class.java, { mock<RedissonClient>() })
        .withUserConfiguration(LoginAttemptLimitConfig::class.java)

    @BeforeEach
    fun flush() {
        redissonClient.keys.flushall()
    }

    @Test
    fun 설정이_없으면_쌍은_10번_이메일은_50번_창과_차단은_15분이다() {
        contextRunner.run { context ->
            val properties = context.getBean(LoginAttemptLimitProperties::class.java)

            assertThat(properties.maxFailuresPerEmailAndIp).isEqualTo(10)
            assertThat(properties.maxFailuresPerEmail).isEqualTo(50)
            assertThat(properties.window).isEqualTo(Duration.ofMinutes(15))
            assertThat(properties.blockDuration).isEqualTo(Duration.ofMinutes(15))
        }
    }

    @Test
    fun 설정한_값으로_횟수와_시간을_바꾼다() {
        contextRunner.withPropertyValues(
            "user.login-attempt.max-failures-per-email-and-ip=3",
            "user.login-attempt.max-failures-per-email=7",
            "user.login-attempt.window=5m",
            "user.login-attempt.block-duration=30m"
        ).run { context ->
            val properties = context.getBean(LoginAttemptLimitProperties::class.java)

            assertThat(properties.maxFailuresPerEmailAndIp).isEqualTo(3)
            assertThat(properties.maxFailuresPerEmail).isEqualTo(7)
            assertThat(properties.window).isEqualTo(Duration.ofMinutes(5))
            assertThat(properties.blockDuration).isEqualTo(Duration.ofMinutes(30))
        }
    }

    @Test
    fun 횟수를_0으로_두면_기동하지_않는다() {
        contextRunner.withPropertyValues("user.login-attempt.max-failures-per-email=0").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun 제한기는_설정한_쌍_횟수와_이메일_횟수와_차단_시간으로_막는다() {
        val limiter = RedisLoginAttemptLimiter(
            redissonClient,
            Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC),
            LoginAttemptLimitProperties(maxFailuresPerEmailAndIp = 3, maxFailuresPerEmail = 5, blockDuration = Duration.ofMinutes(30))
        )

        repeat(3) { limiter.recordFailure(EMAIL, "10.0.0.1") }
        val pairBlocked = limiter.remainingBlock(EMAIL, "10.0.0.1")
        val otherIpAfterPair = limiter.remainingBlock(EMAIL, "10.0.0.2")
        repeat(2) { limiter.recordFailure(EMAIL, "10.0.0.3") }
        val otherIpAfterEmail = limiter.remainingBlock(EMAIL, "10.0.0.2")

        assertThat(pairBlocked).isEqualTo(Duration.ofMinutes(30))
        assertThat(otherIpAfterPair).isEqualTo(Duration.ZERO)
        assertThat(otherIpAfterEmail).isEqualTo(Duration.ofMinutes(30))
    }

    companion object {
        private const val EMAIL = "member@example.com"

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
