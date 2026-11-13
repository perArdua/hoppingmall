package com.hoppingmall.user.config.ratelimit

import com.hoppingmall.common.UserPrincipal
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerInterceptor

class RateLimitInterceptor(
    private val proxyManager: ProxyManager<String>,
    private val trustedProxyCount: Int = 0
) : HandlerInterceptor {

    init {
        require(trustedProxyCount >= 0) { "user.rate-limit.trusted-proxy-count는 0 이상이어야 합니다: $trustedProxyCount" }
    }

    private val pathMatcher = AntPathMatcher()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val policy = findMatchingPolicy(request) ?: return true

        val key = resolveKey(policy, request) ?: return true
        val bucketKey = "${policy.pathPattern}:${policy.httpMethod}:$key"

        val bucket = proxyManager.builder()
            .build(bucketKey) { buildConfiguration(policy) }

        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (!probe.isConsumed) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            val retryAfterSeconds = probe.nanosToWaitForRefill / 1_000_000_000 + 1
            response.setHeader("Retry-After", retryAfterSeconds.toString())
            response.writer.write("""{"code":"FAILURE","message":"요청이 너무 많습니다. ${retryAfterSeconds}초 후 다시 시도해주세요.","data":null}""")
            return false
        }

        return true
    }

    private fun findMatchingPolicy(request: HttpServletRequest): RateLimitPolicy? {
        val requestPath = request.requestURI
        val requestMethod = request.method
        return RateLimitPolicy.POLICIES.find { policy ->
            policy.httpMethod.equals(requestMethod, ignoreCase = true)
                    && pathMatcher.match(policy.pathPattern, requestPath)
        }
    }

    private fun resolveKey(policy: RateLimitPolicy, request: HttpServletRequest): String? {
        return when (policy.keyType) {
            RateLimitKeyType.IP -> resolveClientIp(request)
            RateLimitKeyType.USER_ID -> {
                val auth = SecurityContextHolder.getContext().authentication ?: return null
                val principal = auth.principal as? UserPrincipal ?: return null
                principal.getUserId().toString()
            }
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        if (trustedProxyCount == 0) {
            return request.remoteAddr
        }
        val forwarded = request.getHeaders(FORWARDED_FOR_HEADER).toList()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (forwarded.size < trustedProxyCount) {
            return request.remoteAddr
        }
        return forwarded[forwarded.size - trustedProxyCount]
    }

    private fun buildConfiguration(policy: RateLimitPolicy): BucketConfiguration {
        return BucketConfiguration.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(policy.capacity)
                    .refillGreedy(policy.refillTokens, policy.refillDuration)
                    .build()
            )
            .build()
    }

    companion object {
        private const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
    }
}
