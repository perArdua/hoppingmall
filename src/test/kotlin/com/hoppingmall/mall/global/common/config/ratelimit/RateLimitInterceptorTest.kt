package com.hoppingmall.mall.global.common.config.ratelimit

import com.hoppingmall.mall.global.auth.UserPrincipal
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration
import java.util.function.Supplier

@DisplayName("RateLimitInterceptor")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RateLimitInterceptorTest {

    private val proxyManager: ProxyManager<String> = mock()
    private val interceptor = RateLimitInterceptor(proxyManager)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun mockBucketWithCapacity(capacity: Long, consumeFirst: Int = 0): Pair<RemoteBucketBuilder<String>, BucketProxy> {
        val localBucket = io.github.bucket4j.Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(1)).build())
            .build()
        repeat(consumeFirst) { localBucket.tryConsume(1) }
        val probe = localBucket.tryConsumeAndReturnRemaining(1)

        val bucketProxy: BucketProxy = mock()
        val builder: RemoteBucketBuilder<String> = mock()
        whenever(proxyManager.builder()).thenReturn(builder)
        whenever(builder.build(any<String>(), any<Supplier<BucketConfiguration>>())).thenReturn(bucketProxy)
        whenever(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe)

        return builder to bucketProxy
    }

    @Nested
    @DisplayName("preHandle")
    inner class PreHandle {

        @Test
        fun 정책에_해당하지_않는_경로는_통과한다() {
            val request = MockHttpServletRequest("GET", "/api/v1/products")
            val response = MockHttpServletResponse()

            val result = interceptor.preHandle(request, response, Any())

            assertTrue(result)
            verifyNoInteractions(proxyManager)
        }

        @Test
        fun IP_기반_정책에서_요청이_허용되면_통과한다() {
            val request = MockHttpServletRequest("POST", "/api/v1/users/login")
            request.remoteAddr = "192.168.1.1"
            val response = MockHttpServletResponse()
            mockBucketWithCapacity(5)

            val result = interceptor.preHandle(request, response, Any())

            assertTrue(result)
        }

        @Test
        fun 요청_횟수_초과_시_429_응답을_반환한다() {
            val request = MockHttpServletRequest("POST", "/api/v1/users/login")
            request.remoteAddr = "192.168.1.1"
            val response = MockHttpServletResponse()
            mockBucketWithCapacity(1, consumeFirst = 1)

            val result = interceptor.preHandle(request, response, Any())

            assertFalse(result)
            assertEquals(429, response.status)
            assertTrue(response.contentAsString.contains("요청이 너무 많습니다"))
        }

        @Test
        fun USER_ID_기반_정책에서_인증_정보가_없으면_통과한다() {
            val request = MockHttpServletRequest("POST", "/api/v1/orders")
            val response = MockHttpServletResponse()
            SecurityContextHolder.clearContext()

            val result = interceptor.preHandle(request, response, Any())

            assertTrue(result)
            verifyNoInteractions(proxyManager)
        }

        @Test
        fun X_Forwarded_For_헤더가_있으면_첫번째_IP를_사용한다() {
            val request = MockHttpServletRequest("POST", "/api/v1/users/login")
            request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
            val response = MockHttpServletResponse()
            val (builder, _) = mockBucketWithCapacity(5)

            interceptor.preHandle(request, response, Any())

            verify(builder).build(eq("/api/v1/users/login:POST:10.0.0.1"), any<Supplier<BucketConfiguration>>())
        }

        @Test
        fun USER_ID_기반_정책에서_인증된_사용자는_userId로_제한한다() {
            val request = MockHttpServletRequest("POST", "/api/v1/orders")
            val response = MockHttpServletResponse()
            val userPrincipal = UserPrincipal(42L, "test@test.com", "ROLE_BUYER")
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.authorities)
            val (builder, _) = mockBucketWithCapacity(5)

            interceptor.preHandle(request, response, Any())

            verify(builder).build(eq("/api/v1/orders:POST:42"), any<Supplier<BucketConfiguration>>())
        }

        @Test
        fun 와일드카드_경로_패턴이_매칭된다() {
            val request = MockHttpServletRequest("POST", "/api/v1/coupons/123/issue")
            val response = MockHttpServletResponse()
            val userPrincipal = UserPrincipal(1L, "test@test.com", "ROLE_BUYER")
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.authorities)
            val (builder, _) = mockBucketWithCapacity(5)

            interceptor.preHandle(request, response, Any())

            verify(builder).build(eq("/api/v1/coupons/*/issue:POST:1"), any<Supplier<BucketConfiguration>>())
        }
    }
}
