package com.hoppingmall.consistency.auth

import com.hoppingmall.consistency.support.RedisContainerSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

@DisplayName("AdminTokenFilter 로그아웃 토큰")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminTokenFilterBlacklistTest {

    private val factories = mutableListOf<LettuceConnectionFactory>()
    private val registry = SimpleMeterRegistry()

    @AfterEach
    fun tearDown() {
        factories.forEach { it.destroy() }
    }

    private fun redisTemplate(host: String, port: Int): StringRedisTemplate {
        val clientConfiguration = LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build()
        val factory = LettuceConnectionFactory(RedisStandaloneConfiguration(host, port), clientConfiguration)
        factory.afterPropertiesSet()
        factories.add(factory)
        return StringRedisTemplate(factory)
    }

    private fun filter(redisTemplate: StringRedisTemplate) =
        AdminTokenFilter(TestTokens.SECRET, redisTemplate, registry)

    private fun call(filter: AdminTokenFilter, token: String): Pair<Int, Boolean> {
        val request = MockHttpServletRequest("GET", "/api/v1/violations")
        request.addHeader(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token))
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return response.status to (chain.request != null)
    }

    private fun failures(): Double =
        registry.find(BLACKLIST_REDIS_FAILURE_METRIC).counter()?.count() ?: 0.0

    @Test
    fun 로그아웃한_관리자_토큰은_만료_전이어도_401이고_위반_API로_넘기지_않는다() {
        val redisTemplate = redisTemplate(RedisContainerSupport.host, RedisContainerSupport.port)
        val token = TestTokens.admin(userId = 41L)
        redisTemplate.opsForValue().set("blacklist:$token", "blacklisted", Duration.ofMinutes(1))

        assertThat(call(filter(redisTemplate), token)).isEqualTo(401 to false)
    }

    @Test
    fun 로그아웃하지_않은_관리자_토큰은_그대로_통과한다() {
        val redisTemplate = redisTemplate(RedisContainerSupport.host, RedisContainerSupport.port)

        assertThat(call(filter(redisTemplate), TestTokens.admin(userId = 42L))).isEqualTo(200 to true)
        assertThat(failures()).isZero()
    }

    @Test
    fun 로그아웃_목록_Redis_조회가_실패하면_게이트웨이처럼_통과시키고_실패_수를_센다() {
        val unreachable = redisTemplate("127.0.0.1", 1)

        assertThat(call(filter(unreachable), TestTokens.admin(userId = 43L))).isEqualTo(200 to true)
        assertThat(failures()).isEqualTo(1.0)
    }

    companion object {
        private const val BLACKLIST_REDIS_FAILURE_METRIC = "consistency.jwt.blacklist.redis.failures"
    }
}
