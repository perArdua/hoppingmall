package com.hoppingmall.user.config.ratelimit

import com.hoppingmall.user.support.InMemoryRateLimitBuckets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("요청 제한 인터셉터 믿는 프록시 단수에 따른 IP 키")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RateLimitInterceptorTrustedProxyTest {

    private val buckets = InMemoryRateLimitBuckets()

    @Test
    fun 단수가_1이면_프록시가_덧붙인_주소가_키가_되고_클라이언트가_앞에_끼운_가짜_주소는_무시된다() {
        val interceptor = RateLimitInterceptor(buckets.proxyManager(), trustedProxyCount = 1)

        val statuses = (1..6).map { attempt ->
            login(interceptor, remoteAddr = "10.0.0.2", forwardedFor = listOf("203.0.113.$attempt, 198.51.100.7"))
        }

        assertThat(statuses.take(5)).containsOnly(200)
        assertThat(statuses.last()).isEqualTo(429)
        assertThat(buckets.keys).containsExactly("/api/v1/users/login:POST:198.51.100.7")
    }

    @Test
    fun 단수가_1이면_프록시가_덧붙인_주소가_다를_때_한도를_따로_센다() {
        val interceptor = RateLimitInterceptor(buckets.proxyManager(), trustedProxyCount = 1)
        repeat(5) { login(interceptor, remoteAddr = "10.0.0.2", forwardedFor = listOf("198.51.100.7")) }

        val otherStatus = login(interceptor, remoteAddr = "10.0.0.2", forwardedFor = listOf("198.51.100.8"))

        assertThat(otherStatus).isEqualTo(200)
        assertThat(buckets.keys).containsExactlyInAnyOrder(
            "/api/v1/users/login:POST:198.51.100.7",
            "/api/v1/users/login:POST:198.51.100.8"
        )
    }

    @Test
    fun 단수가_2이면_여러_헤더_줄을_이어_오른쪽에서_두_번째_주소가_키다() {
        val interceptor = RateLimitInterceptor(buckets.proxyManager(), trustedProxyCount = 2)

        login(interceptor, remoteAddr = "10.0.0.2", forwardedFor = listOf("1.2.3.4, 198.51.100.7", " 172.16.0.3 "))

        assertThat(buckets.keys).containsExactly("/api/v1/users/login:POST:198.51.100.7")
    }

    @Test
    fun 덧붙은_주소가_단수보다_적으면_연결_주소가_키다() {
        val interceptor = RateLimitInterceptor(buckets.proxyManager(), trustedProxyCount = 2)

        login(interceptor, remoteAddr = "10.0.0.2", forwardedFor = listOf("198.51.100.7"))
        login(interceptor, remoteAddr = "10.0.0.2", forwardedFor = emptyList())

        assertThat(buckets.keys).containsExactly("/api/v1/users/login:POST:10.0.0.2")
    }

    @Test
    fun 단수가_0이면_헤더를_보지_않고_연결_주소가_키다() {
        val interceptor = RateLimitInterceptor(buckets.proxyManager(), trustedProxyCount = 0)

        login(interceptor, remoteAddr = "10.0.0.2", forwardedFor = listOf("198.51.100.7"))

        assertThat(buckets.keys).containsExactly("/api/v1/users/login:POST:10.0.0.2")
    }

    @Test
    fun 단수가_음수면_인터셉터를_만들_수_없다() {
        assertThatThrownBy { RateLimitInterceptor(buckets.proxyManager(), trustedProxyCount = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun login(interceptor: RateLimitInterceptor, remoteAddr: String, forwardedFor: List<String>): Int {
        val request = MockHttpServletRequest("POST", "/api/v1/users/login").apply {
            this.remoteAddr = remoteAddr
            forwardedFor.forEach { addHeader("X-Forwarded-For", it) }
        }
        val response = MockHttpServletResponse()
        interceptor.preHandle(request, response, Any())
        return response.status
    }
}
