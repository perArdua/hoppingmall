package com.hoppingmall.user.config.ratelimit

import com.hoppingmall.user.support.InMemoryRateLimitBuckets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("요청 제한 인터셉터 IP 키 기본 동작")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RateLimitInterceptorTest {

    private val buckets = InMemoryRateLimitBuckets()
    private val interceptor = RateLimitInterceptor(buckets.proxyManager())

    @Test
    fun X_Forwarded_For만_바꿔_로그인을_6번_요청하면_6번째는_429다() {
        val statuses = (1..6).map { attempt ->
            login(remoteAddr = "10.0.0.5", forwardedFor = "203.0.113.$attempt")
        }

        assertThat(statuses.take(5)).containsOnly(200)
        assertThat(statuses.last()).isEqualTo(429)
        assertThat(buckets.keys).containsExactly("/api/v1/users/login:POST:10.0.0.5")
    }

    @Test
    fun 다른_사람의_IP를_X_Forwarded_For에_넣어도_그_사람의_한도를_소진시키지_못한다() {
        repeat(6) { login(remoteAddr = "10.0.0.5", forwardedFor = "198.51.100.9") }

        val victimStatus = login(remoteAddr = "198.51.100.9", forwardedFor = null)

        assertThat(victimStatus).isEqualTo(200)
    }

    @Test
    fun 연결_주소가_다르면_한도를_따로_센다() {
        repeat(5) { login(remoteAddr = "10.0.0.5", forwardedFor = null) }

        val otherStatus = login(remoteAddr = "10.0.0.6", forwardedFor = null)

        assertThat(otherStatus).isEqualTo(200)
        assertThat(login(remoteAddr = "10.0.0.5", forwardedFor = null)).isEqualTo(429)
    }

    private fun login(remoteAddr: String, forwardedFor: String?): Int {
        val request = MockHttpServletRequest("POST", "/api/v1/users/login").apply {
            this.remoteAddr = remoteAddr
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
        }
        val response = MockHttpServletResponse()
        interceptor.preHandle(request, response, Any())
        return response.status
    }
}
