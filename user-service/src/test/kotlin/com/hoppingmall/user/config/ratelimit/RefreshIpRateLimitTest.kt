package com.hoppingmall.user.config.ratelimit

import com.hoppingmall.user.support.InMemoryRateLimitBuckets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("재발급 요청과 user-service IP 한도")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshIpRateLimitTest {

    private val buckets = InMemoryRateLimitBuckets()
    private val interceptor = RateLimitInterceptor(buckets.proxyManager())

    @Test
    fun 게이트웨이_주소_하나로_들어온_재발급이_1분에_여섯_번을_넘어도_막지_않는다() {
        val statuses = (1..10).map { refresh(remoteAddr = "10.0.0.2") }

        assertThat(statuses).containsOnly(200)
        assertThat(buckets.keys).isEmpty()
    }

    @Test
    fun 재발급을_많이_해도_같은_주소의_로그인_한도는_따로_남는다() {
        repeat(10) { refresh(remoteAddr = "10.0.0.2") }

        assertThat(login(remoteAddr = "10.0.0.2")).isEqualTo(200)
    }

    private fun refresh(remoteAddr: String): Int = call("/api/v1/auth/refresh", remoteAddr)

    private fun login(remoteAddr: String): Int = call("/api/v1/users/login", remoteAddr)

    private fun call(path: String, remoteAddr: String): Int {
        val request = MockHttpServletRequest("POST", path).apply { this.remoteAddr = remoteAddr }
        val response = MockHttpServletResponse()
        interceptor.preHandle(request, response, Any())
        return response.status
    }
}
