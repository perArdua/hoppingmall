package com.hoppingmall.mall.global.common.config.ratelimit

import com.hoppingmall.mall.global.auth.UserPrincipal
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
    private val proxyManager: ProxyManager<String>
) : HandlerInterceptor {

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
            RateLimitKeyType.IP -> request.getHeader("X-Forwarded-For")?.split(",")?.first()?.trim()
                ?: request.remoteAddr
            RateLimitKeyType.USER_ID -> {
                val auth = SecurityContextHolder.getContext().authentication ?: return null
                val principal = auth.principal as? UserPrincipal ?: return null
                principal.getUserId().toString()
            }
        }
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
}
